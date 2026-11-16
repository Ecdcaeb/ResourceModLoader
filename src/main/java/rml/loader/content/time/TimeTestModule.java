package rml.loader.content.time;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rml.loader.content.CompiledResource;
import rml.loader.content.ContentCatalog;
import rml.loader.content.CompiledStructureModule;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;

import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import com.google.gson.reflect.TypeToken;
import rml.deserializer.value.WeightedList;
import java.lang.reflect.Type;
import java.util.Random;
import rml.deserializer.value.DoubleRange;
import rml.loader.deserialize.NativeTypeDecoders.BlockMatcher;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.FloatProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Native ticking, persistent clocks, scheduled timelines, and asynchronous in-world tests. */
public final class TimeTestModule {
    private static final Set<String> TYPES;
    static {
        Set<String> types = new HashSet<>();
        Collections.addAll(types, "world_clock", "timeline", "test_environment", "test_instance");
        TYPES = Collections.unmodifiableSet(types);
    }
    private final ContentCatalog catalog;
    private final DataRules rules;
    private final CompiledTagModule tags;
    private final CompiledStructureModule structures;
    private final Map<ResourceLocation, ClockDef> clocks = new LinkedHashMap<>();
    private final Map<ResourceLocation, TimelineDef> timelines = new LinkedHashMap<>();
    private final Map<ResourceLocation, Environment> environments = new LinkedHashMap<>();
    private final Map<ResourceLocation, TestDef> tests = new LinkedHashMap<>();
    private final Map<WorldServer, List<RunningTest>> running = new IdentityHashMap<>();
    private final Map<String, ScenarioResult> scenarioResults = new LinkedHashMap<>();
    private static final int MAX_SCENARIO_RESULTS = 128;
    private boolean initialized;
    private DeserializerManager deserializers;
    private final Set<WorldServer> unloaded = Collections.newSetFromMap(new IdentityHashMap<WorldServer, Boolean>());

    public TimeTestModule(ContentCatalog catalog, DataRules rules, CompiledTagModule tags, CompiledStructureModule structures) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.structures = Objects.requireNonNull(structures, "structures");
    }

    public Set<String> getTypes() { return TYPES; }
    public Set<String> getCommands() { Set<String> commands=new HashSet<>(); Collections.addAll(commands,"clock","timeline","test"); return Collections.unmodifiableSet(commands); }

    public void registerDecoders(DeserializerManager manager) {
        Objects.requireNonNull(manager, "manager");
        if (deserializers != null && deserializers != manager) throw new IllegalStateException("TimeTestModule is already bound to a decoder manager");
        if (deserializers != null) return;
        deserializers = manager;
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "world_clock"), ClockDef.class, json -> ClockDef.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "timeline"), TimelineDef.class, json -> TimelineDef.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "timeline_event"), TimelineEvent.class, json -> TimelineEvent.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "time_block_change"), BlockChange.class, json -> BlockChange.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "test_environment"), Environment.class, json -> Environment.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "test_instance"), TestDef.class, json -> TestDef.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "test_assertion"), Assertion.class, json -> Assertion.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "time_offset"), TimeOffset.class, json -> TimeOffset.decode(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "test_step"), TestStep.class, json -> TestStep.decode(manager, json)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "clock_time"), DataRules.NumberValue.class, json -> {
            JsonObject object=fields(json,"clock time","clock","fractional");
            ResourceLocation id=manager.decode(ResourceLocation.class,object.get("clock"));
            boolean fractional=!object.has("fractional")||manager.decode(Boolean.class,object.get("fractional"));
            return context -> {
                WorldServer world=context.getWorld();
                ClockDef definition=clocks.get(id);
                if(!initialized||world==null||definition==null||definition.dimension!=world.provider.getDimension()||unloaded.contains(world))throw new IllegalArgumentException("Clock is unavailable: "+id);
                TimeWorldData data=TimeWorldData.get(world);
                TimeWorldData.Clock clock=clock(data,id);
                if(!clock.configured)throw new IllegalArgumentException("Clock has not started: "+id);
                return clock.time+(fractional?clock.fraction:0);
            };
        }));
    }
    /** Validate every definition before publishing any of them to the running module. */
    public void initialize() {
        if (initialized) return;
        if (deserializers == null) throw new IllegalStateException("Time decoders must be registered before initialization");
        Map<ResourceLocation, ClockDef> parsedClocks = new LinkedHashMap<>();
        Map<ResourceLocation, TimelineDef> parsedTimelines = new LinkedHashMap<>();
        Map<ResourceLocation, Environment> parsedEnvironments = new LinkedHashMap<>();
        Map<ResourceLocation, TestDef> parsedTests = new LinkedHashMap<>();
        for (String type : TYPES) for (CompiledResource resource : catalog.snapshot().resources(type).values()) {
            try {
                JsonElement value=resource.getValue();
                switch (type) {
                    case "world_clock": parsedClocks.put(resource.getId(), deserializers.decode(ClockDef.class,value)); break;
                    case "timeline": parsedTimelines.put(resource.getId(), deserializers.decode(TimelineDef.class,value)); break;
                    case "test_environment": parsedEnvironments.put(resource.getId(), deserializers.decode(Environment.class,value)); break;
                    case "test_instance": parsedTests.put(resource.getId(), deserializers.decode(TestDef.class,value)); break;
                    default: throw new IllegalStateException(type);
                }
            } catch (Exception error) {
                throw new IllegalArgumentException(resource.getSource() + ": " + type + " " + resource.getId() + ": " + error.getMessage(), error);
            }
        }
        Set<Integer> clockDimensions = new HashSet<>();
        for (Map.Entry<ResourceLocation, ClockDef> entry : parsedClocks.entrySet()) {
            if (entry.getValue().synchronizeWorld && !clockDimensions.add(entry.getValue().dimension)) throw new IllegalArgumentException(source("world_clock",entry.getKey())+": multiple clocks synchronize native world time in dimension "+entry.getValue().dimension);
        }
        for (Map.Entry<ResourceLocation, TimelineDef> entry : parsedTimelines.entrySet()) {
            if (!parsedClocks.containsKey(entry.getValue().clock)) throw new IllegalArgumentException(source("timeline",entry.getKey())+": timeline "+entry.getKey()+" references missing world_clock "+entry.getValue().clock);
        }
        for (Map.Entry<ResourceLocation, TestDef> entry : parsedTests.entrySet()) {
            if (!parsedEnvironments.containsKey(entry.getValue().environment)) throw new IllegalArgumentException(source("test_instance",entry.getKey())+": test_instance "+entry.getKey()+" references missing test_environment "+entry.getValue().environment);
        }
        clocks.clear(); timelines.clear(); environments.clear(); tests.clear(); unloaded.clear();
        clocks.putAll(parsedClocks); timelines.putAll(parsedTimelines); environments.putAll(parsedEnvironments); tests.putAll(parsedTests);
        MinecraftForge.EVENT_BUS.register(this);
        initialized = true;
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!initialized) throw new CommandException("Time module is stopped");
        if (args.length == 0) throw new WrongUsageException("rml <clock|timeline|test> ...");
        switch (args[0]) {
            case "clock": executeClock(server, sender, args); return;
            case "timeline": executeTimeline(server, sender, args); return;
            case "test": executeTest(server, sender, args); return;
            default: throw new WrongUsageException("Unknown time command: " + args[0]);
        }
    }

    private void executeClock(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) throw new WrongUsageException("clock <query|pause|resume|rate|set|add> <id> [value]");
        ResourceLocation id = id(args[2]); ClockDef definition=require(clocks,id,"world_clock");
        WorldServer world = sender.getEntityWorld() instanceof WorldServer ? (WorldServer)sender.getEntityWorld() : server.getWorld(0);
        if (world == null) throw new CommandException("No server world is available");
        if(definition.dimension!=world.provider.getDimension()) throw new CommandException("Clock "+id+" belongs to dimension "+definition.dimension);
        TimeWorldData data = TimeWorldData.get(world);
        TimeWorldData.Clock clock = clock(data, id);
        if(!clock.configured) { configureClock(world,id,definition,clock); if(definition.synchronizeWorld)world.setWorldTime(clock.time); data.markDirty(); }
        switch (args[1]) {
            case "query": sender.sendMessage(new net.minecraft.util.text.TextComponentString(id + " time=" + clock.time + " rate=" + clock.rate + " paused=" + clock.paused)); return;
            case "pause": clock.paused=true; break;
            case "resume": clock.paused=false; break;
            case "rate": { if (args.length < 4) throw new WrongUsageException("clock rate <id> <number>"); double rate=number(args[3]); if (rate < 0 || rate > 1000) throw new CommandException("Clock rate must be between 0 and 1000"); clock.rate=rate; clock.fraction=0; break; }
            case "set": if (args.length < 4) throw new WrongUsageException("clock set <id> <ticks>"); clock.time=longNumber(args[3]); clock.fraction=0; if(definition.synchronizeWorld)world.setWorldTime(clock.time); break;
            case "add": if (args.length < 4) throw new WrongUsageException("clock add <id> <ticks>"); try { clock.time=Math.addExact(clock.time,longNumber(args[3])); } catch(ArithmeticException failure) { throw new CommandException("Clock time overflow"); } clock.fraction=0; if(definition.synchronizeWorld)world.setWorldTime(clock.time); break;
            default: throw new WrongUsageException("clock <query|pause|resume|rate|set|add> <id> [value]");
        }
        data.markDirty(); sender.sendMessage(new net.minecraft.util.text.TextComponentString("Clock " + id + " updated"));
    }

    private void executeTimeline(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !"query".equals(args[1])) throw new WrongUsageException("timeline query <id>");
        ResourceLocation id=id(args[2]); TimelineDef def=require(timelines,id,"timeline");
        WorldServer world=sender.getEntityWorld() instanceof WorldServer?(WorldServer)sender.getEntityWorld():server.getWorld(0);
        if(world==null)throw new CommandException("No server world is available");
        TimeWorldData.Timeline state=TimeWorldData.get(world).timelines.get(id);
        sender.sendMessage(new net.minecraft.util.text.TextComponentString(id+" clock="+def.clock+" event="+(state==null?0:state.eventIndex)+" cycle="+(state==null?0:state.cycle)));
    }

    private void executeTest(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String owner = "command:" + (sender.getCommandSenderEntity() == null ? "console" : sender.getCommandSenderEntity().getUniqueID());
        JsonObject result;
        if ((args.length == 3 || args.length == 6) && "run".equals(args[1])) {
            BlockPos origin = args.length == 6 ? new BlockPos(commandInteger(args[3]), commandInteger(args[4]), commandInteger(args[5])) : null;
            result = startScenario(server, sender, owner, UUID.randomUUID().toString(), id(args[2]), origin);
        } else if (args.length == 3 && "status".equals(args[1])) {
            result = scenarioStatus(server, owner, args[2]);
        } else if (args.length == 3 && "cancel".equals(args[1])) {
            result = cancelScenario(server, owner, args[2]);
        } else throw new WrongUsageException("test run <scenario> [x y z] | test <status|cancel> <run_id>");
        sender.sendMessage(new net.minecraft.util.text.TextComponentString(result.toString()));
    }

    /** Start/query/cancel on the server thread. The opaque owner is supplied by the authorized caller. */
    public JsonObject startScenario(MinecraftServer server, ICommandSender sender, String owner, String runId,
                                    ResourceLocation id, BlockPos requestedOrigin) throws CommandException {
        requireServerThread(server);
        if (!initialized) throw new CommandException("Time module is stopped");
        Objects.requireNonNull(owner, "owner");
        if (!UUID.fromString(runId).toString().equals(runId)) throw new CommandException("Invalid scenario run id");
        if (scenarioResults.containsKey(runId)) throw new CommandException("scenario_run_exists");
        TestDef def = require(tests, id, "test_instance");
        WorldServer world = sender.getEntityWorld() instanceof WorldServer ? (WorldServer) sender.getEntityWorld() : server.getWorld(0);
        if (world == null) throw new CommandException("No server world is available");
        if (unloaded.contains(world)) throw new CommandException("World is unloading");
        List<RunningTest> active = running.get(world);
        if (active != null && !active.isEmpty()) throw new CommandException("A test is already running in this dimension");
        if (scenarioResults.size() >= MAX_SCENARIO_RESULTS) {
            Iterator<ScenarioResult> retained = scenarioResults.values().iterator();
            while (retained.hasNext()) {
                if (!"running".equals(retained.next().status)) { retained.remove(); break; }
            }
            if (scenarioResults.size() >= MAX_SCENARIO_RESULTS) throw new CommandException("scenario_capacity_reached");
        }
        ScenarioResult result = new ScenarioResult(runId, owner, id, def, world.provider.getDimension());
        RunningTest run = new RunningTest(id, def, environments.get(def.environment), sender, requestedOrigin, result);
        scenarioResults.put(runId, result);
        active = running.computeIfAbsent(world, key -> new ArrayList<>());
        active.add(run);
        run.begin(world);
        if (run.cancelled) { active.remove(run); if (active.isEmpty()) running.remove(world); }
        return result.snapshot();
    }

    public JsonObject scenarioStatus(MinecraftServer server, String owner, String runId) throws CommandException {
        requireServerThread(server);
        return ownedScenario(owner, runId).snapshot();
    }

    public JsonArray scenarioRuns(MinecraftServer server, String owner) {
        requireServerThread(server);
        JsonArray result = new JsonArray();
        for (ScenarioResult run : scenarioResults.values()) if (run.owner.equals(owner)) result.add(run.snapshot());
        return result;
    }

    public JsonObject cancelScenario(MinecraftServer server, String owner, String runId) throws CommandException {
        requireServerThread(server);
        ScenarioResult result = ownedScenario(owner, runId);
        if ("running".equals(result.status)) {
            Iterator<Map.Entry<WorldServer, List<RunningTest>>> worlds = running.entrySet().iterator();
            while (worlds.hasNext()) {
                Map.Entry<WorldServer, List<RunningTest>> entry = worlds.next();
                List<RunningTest> active = entry.getValue();
                for (int i = 0; i < active.size(); i++) if (active.get(i).result == result) {
                    RunningTest run = active.remove(i);
                    WorldServer world = entry.getKey();
                    if (active.isEmpty()) worlds.remove();
                    run.cancel(world);
                    return result.snapshot();
                }
            }
            throw new IllegalStateException("Running scenario has no active test");
        }
        return result.snapshot();
    }

    private ScenarioResult ownedScenario(String owner, String runId) throws CommandException {
        ScenarioResult result = scenarioResults.get(runId);
        if (result == null || !result.owner.equals(owner)) throw new CommandException("scenario_not_found");
        return result;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isCallingFromMinecraftThread()) throw new IllegalStateException("Scenario operations require the server thread");
    }

    private void configureClock(WorldServer world,ResourceLocation id,ClockDef definition,TimeWorldData.Clock clock) {
        Random random=new Random(world.getSeed()^id.hashCode());
        clock.time=ticks(definition.initial.evaluate(new DataContext(world,null,world.getSpawnPoint(),random,0)));
        clock.epoch=clock.time; clock.rate=definition.rate.sample(random); clock.paused=definition.paused; clock.configured=true;
    }

    @SubscribeEvent public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!initialized || event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        WorldServer world=(WorldServer)event.world;
        if(unloaded.contains(world))return;
        TimeWorldData data=TimeWorldData.get(world);
        for(Map.Entry<ResourceLocation,ClockDef> entry:clocks.entrySet()) {
            if(entry.getValue().dimension!=world.provider.getDimension())continue;
            TimeWorldData.Clock clock=clock(data,entry.getKey());
            if(!clock.configured) { configureClock(world,entry.getKey(),entry.getValue(),clock); data.markDirty(); }
            if(!clock.paused && clock.rate!=0) {
                try { clock.advance(); } catch(ArithmeticException failure) { clock.paused=true; }
                data.markDirty();
            }
            if(entry.getValue().synchronizeWorld)world.setWorldTime(clock.time);
        }
        for(Map.Entry<ResourceLocation,TimelineDef> entry:timelines.entrySet()) {
            if(!initialized||unloaded.contains(world))return;
            ClockDef clock=clocks.get(entry.getValue().clock);
            if(clock!=null&&clock.dimension==world.provider.getDimension())tickTimeline(world,data,entry.getKey(),entry.getValue());
        }
        List<RunningTest> active=running.get(world);
        if(active!=null) {
            for(int i=active.size()-1;i>=0&&initialized&&!unloaded.contains(world);i--) {
                RunningTest test=active.get(i);
                if(test.tick(world))active.remove(test);
            }
            if(active.isEmpty())running.remove(world);
        }
    }

    private void tickTimeline(WorldServer world, TimeWorldData data, ResourceLocation id, TimelineDef def) {
        TimeWorldData.Clock clock=clock(data,def.clock);
        TimeWorldData.Timeline state=data.timelines.computeIfAbsent(id,key->new TimeWorldData.Timeline());
        if(state.completed)return;
        try {
            if(!state.started) { state.previous=clock.epoch; state.started=true; }
            if(state.offsets.length!=def.events.size()) {
                def.schedule(state,new DataContext(world,null,world.getSpawnPoint(),new Random(world.getSeed()^def.seed^id.hashCode()),0));
                data.markDirty();
            }
            // A rewind holds the cursor; already executed events never replay.
            state.lastClock=clock.time;
            for(int dispatched=0;dispatched<4096&&initialized&&!unloaded.contains(world);dispatched++) {
                if(state.eventIndex>=def.events.size()) {
                    if(!def.loop){state.completed=true;data.markDirty();return;}
                    state.previous=Math.addExact(state.previous,state.duration);
                    state.cycle=Math.addExact(state.cycle,1); state.eventIndex=0; data.markDirty();
                }
                long due=Math.addExact(state.previous,state.offsets[state.eventIndex]);
                if(due>clock.time)return;
                int index=state.eventIndex++;
                data.markDirty();
                TimelineEvent event=def.events.get(index);
                NBTTagCompound inputs=new NBTTagCompound();
                inputs.setLong("clock_time",clock.time);inputs.setLong("scheduled_time",due);inputs.setLong("cycle",state.cycle);inputs.setInteger("event_index",index);
                DataContext context=new DataContext(world,null,event.origin(world),new Random(world.getSeed()^def.seed^id.hashCode()^(state.cycle*31+index)),(int)Math.min(state.cycle,Integer.MAX_VALUE)).withInputs(inputs);
                if(!event.condition.test(context))continue;
                if(!initialized||unloaded.contains(world))return;
                if(event.function!=null)rules.invoke(event.function,context);
                if(!initialized||unloaded.contains(world))return;
                for(DataRules.Effect effect:event.effects) {
                    effect.apply(context,null);
                    if(!initialized||unloaded.contains(world))return;
                }
                for(BlockChange block:event.blocks) {
                    BlockPos target=context.getOrigin().add(block.pos); requireHeight(target);
                    world.setBlockState(target,block.choose(context.getRandom()),3);
                    if(!initialized||unloaded.contains(world))return;
                }
                if(event.message!=null)world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString(event.message));
                if(event.setTime!=null) { clock.time=ticks(event.setTime.evaluate(context)); clock.fraction=0; if(clocks.get(def.clock).synchronizeWorld)world.setWorldTime(clock.time); return; }
            }
        } catch(RuntimeException failure) {
            state.completed=true;data.markDirty();
            world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString("Timeline "+id+" stopped: "+failure.getMessage()));
        }
    }

    @SubscribeEvent public void onWorldLoad(WorldEvent.Load event) {
        if(event.getWorld() instanceof WorldServer)unloaded.remove((WorldServer)event.getWorld());
    }
    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event) {
        if(event.getWorld() instanceof WorldServer) {
            WorldServer world=(WorldServer)event.getWorld(); unloaded.add(world);
            List<RunningTest> active=running.remove(world);
            if(active!=null)for(RunningTest test:active)test.cancel(world);
        }
    }
    public void stop() {
        initialized=false;
        MinecraftForge.EVENT_BUS.unregister(this);
        Map<WorldServer,List<RunningTest>> active=new IdentityHashMap<>(running); running.clear();
        for(Map.Entry<WorldServer,List<RunningTest>> entry:active.entrySet())for(RunningTest test:entry.getValue())test.cancel(entry.getKey());
        unloaded.clear();
    }

    private static void requireHeight(BlockPos pos) {
        if(pos.getY()<0||pos.getY()>255)throw new IllegalArgumentException("Position lies outside native world height: "+pos);
    }

    private String source(String type,ResourceLocation id){CompiledResource resource=catalog.snapshot().resources(type).get(id);return resource==null?id.toString():resource.getSource();}
    private static TimeWorldData.Clock clock(TimeWorldData data,ResourceLocation id) { return data.clocks.computeIfAbsent(id,key->new TimeWorldData.Clock()); }
    private static <T>T require(Map<ResourceLocation,T> map,ResourceLocation id,String type)throws CommandException { T found=map.get(id); if(found==null)throw new CommandException("Unknown "+type+": "+id); return found; }
    private static ResourceLocation id(String value)throws CommandException { try{return new ResourceLocation(value);}catch(RuntimeException e){throw new CommandException("Invalid resource identifier: "+value);} }
    private static JsonObject object(JsonElement e,String label){if(e==null||!e.isJsonObject())throw new IllegalArgumentException(label+" must be an object");return e.getAsJsonObject();}
    private static int commandInteger(String value)throws CommandException {try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new CommandException("Expected integer coordinate: "+value);}}
    private static long longNumber(String value)throws CommandException {try{return Long.parseLong(value);}catch(NumberFormatException e){throw new CommandException("Expected integer: "+value);}}
    private static double number(String value)throws CommandException {try{double n=Double.parseDouble(value);if(Double.isNaN(n)||Double.isInfinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new CommandException("Expected finite number: "+value);}}

    private static JsonObject fields(JsonElement json, String label, String... allowed) {
        JsonObject object = object(json, label);
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            boolean supported = "type".equals(field.getKey());
            for (String name : allowed) supported |= name.equals(field.getKey());
            if (!supported) throw new IllegalArgumentException(label + " does not support field " + field.getKey());
            if (field.getValue().isJsonNull()) throw new IllegalArgumentException(label + " field " + field.getKey() + " must not be null");
        }
        return object;
    }
    private static DataRules.NumberValue numberValue(DeserializerManager manager, JsonObject object, String name, double fallback) throws JsonDeserializeException {
        return object.has(name) ? manager.decode(DataRules.NumberValue.class, object.get(name)) : context -> fallback;
    }
    private static DataRules.Effect[] effects(DeserializerManager manager, JsonObject object, String name) throws JsonDeserializeException {
        return object.has(name) ? manager.decode(DataRules.Effect[].class, object.get(name)) : new DataRules.Effect[0];
    }
    private static long ticks(double value) {
        if (!Double.isFinite(value) || value < 0 || value >= Long.MAX_VALUE) throw new IllegalArgumentException("Time must be finite, nonnegative and within long range");
        return (long)Math.ceil(value);
    }
    private static IntProvider boundedProvider(DeserializerManager manager, JsonObject object, String name, int fallback, int min, int max) throws JsonDeserializeException {
        IntProvider provider = object.has(name) ? manager.decode(IntProvider.class, object.get(name)) : new IntProvider(fallback, fallback, random -> fallback);
        if (provider.getMinValue() < min || provider.getMaxValue() > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        return provider;
    }

    /** Offsets compose number values; days are 24000 ticks and cycles use the timeline's cycle_length. */
    public static final class TimeOffset {
        final DataRules.NumberValue ticks, days, cycles;
        TimeOffset(DataRules.NumberValue ticks, DataRules.NumberValue days, DataRules.NumberValue cycles) { this.ticks=ticks; this.days=days; this.cycles=cycles; }
        long evaluate(DataContext context, long cycleLength) { return ticks(ticks.evaluate(context) + days.evaluate(context)*24000D + cycles.evaluate(context)*cycleLength); }
        static TimeOffset decode(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
            if (json.isJsonPrimitive()) return new TimeOffset(manager.decode(DataRules.NumberValue.class,json), context->0, context->0);
            JsonObject o=fields(json,"time offset","ticks","days","cycles");
            if (!o.has("ticks")&&!o.has("days")&&!o.has("cycles")) throw new IllegalArgumentException("time offset requires ticks, days or cycles");
            return new TimeOffset(numberValue(manager,o,"ticks",0),numberValue(manager,o,"days",0),numberValue(manager,o,"cycles",0));
        }
    }
    public static final class ClockDef {
        final DataRules.NumberValue initial; final FloatProvider rate; final boolean paused,synchronizeWorld; final int dimension;
        ClockDef(DataRules.NumberValue initial,FloatProvider rate,boolean paused,int dimension,boolean synchronizeWorld){this.initial=initial;this.rate=rate;this.paused=paused;this.dimension=dimension;this.synchronizeWorld=synchronizeWorld;}
        public static ClockDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"world_clock","initial_time","rate","paused","dimension","synchronize_world");
            FloatProvider rate=o.has("rate")?manager.decode(FloatProvider.class,o.get("rate")):new FloatProvider(1,1,random->1);
            if(rate.getMinValue()<0||rate.getMaxValue()>1000||!Float.isFinite(rate.getMinValue())||!Float.isFinite(rate.getMaxValue()))throw new IllegalArgumentException("rate must be from 0 to 1000");
            return new ClockDef(numberValue(manager,o,"initial_time",0),rate,o.has("paused")&&manager.decode(Boolean.class,o.get("paused")),o.has("dimension")?manager.decode(Integer.class,o.get("dimension")):0,!o.has("synchronize_world")||manager.decode(Boolean.class,o.get("synchronize_world")));
        }
    }
    public static final class TimelineDef {
        final ResourceLocation clock; final boolean loop; final List<TimelineEvent> events; final TimeOffset duration; final long cycleLength, seed;
        TimelineDef(ResourceLocation clock,boolean loop,List<TimelineEvent> events,TimeOffset duration,long cycleLength,long seed){this.clock=clock;this.loop=loop;this.events=events;this.duration=duration;this.cycleLength=cycleLength;this.seed=seed;}
        public static TimelineDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"timeline","clock","loop","events","duration","cycle_length","seed");
            TimelineEvent[] events=manager.decode(TimelineEvent[].class,o.get("events"));
            if(events.length==0)throw new IllegalArgumentException("events must not be empty");
            long cycleLength=o.has("cycle_length")?manager.decode(Long.class,o.get("cycle_length")):24000;
            if(cycleLength<1)throw new IllegalArgumentException("cycle_length must be positive");
            return new TimelineDef(manager.decode(ResourceLocation.class,o.get("clock")),!o.has("loop")||manager.decode(Boolean.class,o.get("loop")),Collections.unmodifiableList(java.util.Arrays.asList(events)),o.has("duration")?manager.decode(TimeOffset.class,o.get("duration")):null,cycleLength,o.has("seed")?manager.decode(Long.class,o.get("seed")):0);
        }
        void schedule(TimeWorldData.Timeline state, DataContext context) {
            state.offsets=new long[events.size()]; long end=-1;
            for(int i=0;i<events.size();i++) {
                long at=events.get(i).at.evaluate(context,cycleLength);
                if(at<end)throw new IllegalArgumentException("event offsets must be ordered");
                state.offsets[i]=at; end=at;
            }
            state.duration=duration==null?Math.addExact(end,1):duration.evaluate(context,cycleLength);
            if(state.duration<=end)throw new IllegalArgumentException("duration must exceed the last event offset");
        }
    }
    public static final class TimelineEvent {
        final TimeOffset at; final ResourceLocation function; final BlockPos offset; final DataRules.NumberValue setTime; final String message; final List<BlockChange> blocks; final DataRules.Test condition; final DataRules.Effect[] effects;
        TimelineEvent(TimeOffset at,ResourceLocation function,BlockPos offset,DataRules.NumberValue setTime,String message,List<BlockChange> blocks,DataRules.Test condition,DataRules.Effect[] effects){this.at=at;this.function=function;this.offset=offset;this.setTime=setTime;this.message=message;this.blocks=blocks;this.condition=condition;this.effects=effects;}
        public static TimelineEvent decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"timeline event","at","function","origin","set_time","message","blocks","condition","effects");
            return new TimelineEvent(manager.decode(TimeOffset.class,o.get("at")),o.has("function")?manager.decode(ResourceLocation.class,o.get("function")):null,o.has("origin")?manager.decode(BlockPos.class,o.get("origin")):BlockPos.ORIGIN,o.has("set_time")?manager.decode(DataRules.NumberValue.class,o.get("set_time")):null,o.has("message")?manager.decode(String.class,o.get("message")):null,java.util.Arrays.asList(o.has("blocks")?manager.decode(BlockChange[].class,o.get("blocks")):new BlockChange[0]),o.has("condition")?manager.decode(DataRules.Test.class,o.get("condition")):context->true,effects(manager,o,"effects"));
        }
        BlockPos origin(WorldServer world){return world.getSpawnPoint().add(offset);}
    }
    public static final class BlockChange {
        public final BlockPos pos; public final IBlockState state; public final WeightedList<IBlockState> states;
        public BlockChange(BlockPos pos,IBlockState state){this(pos,state,null);}
        public BlockChange(BlockPos pos,WeightedList<IBlockState> states){this(pos,null,states);}
        private BlockChange(BlockPos pos,IBlockState state,WeightedList<IBlockState> states){this.pos=pos;this.state=state;this.states=states;}
        public static BlockChange decode(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
            JsonObject value = fields(json, "block change", "pos", "state", "states");
            if (value.has("state") == value.has("states")) throw new IllegalArgumentException("block change requires exactly one of state or states");
            BlockPos position = manager.decode(BlockPos.class, value.get("pos"));
            if (value.has("state")) return new BlockChange(position, manager.decode(IBlockState.class, value.get("state")));
            Type stateType = new TypeToken<WeightedList<IBlockState>>() { }.getType();
            return new BlockChange(position, manager.<WeightedList<IBlockState>>decode(stateType, value.get("states")));
        }
        IBlockState choose(Random random){return states==null?state:states.sample(random);}
    }
    public static final class Environment {
        final ResourceLocation structure; final List<BlockChange> setup; final BlockPos size; final Environment[] environments; final DataRules.Effect[] setupEffects, cleanupEffects;
        Environment(ResourceLocation structure,List<BlockChange> setup,BlockPos size,Environment[] environments,DataRules.Effect[] setupEffects,DataRules.Effect[] cleanupEffects){this.structure=structure;this.setup=setup;this.size=size;this.environments=environments;this.setupEffects=setupEffects;this.cleanupEffects=cleanupEffects;}
        public static Environment decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"test_environment","structure","setup","size","environments","setup_effects","cleanup_effects");
            BlockPos size=o.has("size")?manager.decode(BlockPos.class,o.get("size")):new BlockPos(1,1,1); validateSize(size);
            return new Environment(o.has("structure")?manager.decode(ResourceLocation.class,o.get("structure")):null,java.util.Arrays.asList(o.has("setup")?manager.decode(BlockChange[].class,o.get("setup")):new BlockChange[0]),size,o.has("environments")?manager.decode(Environment[].class,o.get("environments")):new Environment[0],effects(manager,o,"setup_effects"),effects(manager,o,"cleanup_effects"));
        }
    }
    private static void validateSize(BlockPos size) {
        if(size.getX()<1||size.getY()<1||size.getZ()<1||size.getX()>32768||size.getY()>256||size.getZ()>32768||(long)size.getX()*size.getY()*size.getZ()>32768)throw new IllegalArgumentException("environment footprint must contain 1 to 32768 blocks within native height");
    }
    public static final class TestDef {
        final ResourceLocation environment; final IntProvider timeout,retries; final long seed; final List<Assertion> assertions; final TestStep[] sequence; final DataRules.Effect[] setup,cleanup;
        TestDef(ResourceLocation environment,IntProvider timeout,IntProvider retries,long seed,List<Assertion> assertions,TestStep[] sequence,DataRules.Effect[] setup,DataRules.Effect[] cleanup){this.environment=environment;this.timeout=timeout;this.retries=retries;this.seed=seed;this.assertions=assertions;this.sequence=sequence;this.setup=setup;this.cleanup=cleanup;}
        public static TestDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"test_instance","environment","timeout","retries","seed","assertions","sequence","setup","cleanup");
            Assertion[] assertions=o.has("assertions")?manager.decode(Assertion[].class,o.get("assertions")):new Assertion[0];
            TestStep[] sequence=o.has("sequence")?manager.decode(TestStep[].class,o.get("sequence")):new TestStep[0];
            boolean checked=assertions.length>0; for(TestStep step:sequence)checked|=step.assertions.length>0;
            if(!checked)throw new IllegalArgumentException("test requires at least one assertion");
            return new TestDef(manager.decode(ResourceLocation.class,o.get("environment")),boundedProvider(manager,o,"timeout",200,1,120000),boundedProvider(manager,o,"retries",0,0,100),o.has("seed")?manager.decode(Long.class,o.get("seed")):0,java.util.Arrays.asList(assertions),sequence,effects(manager,o,"setup"),effects(manager,o,"cleanup"));
        }
    }
    public static final class TestStep {
        final IntProvider delay; final DataRules.Effect[] effects; final Assertion[] assertions;
        TestStep(IntProvider delay,DataRules.Effect[] effects,Assertion[] assertions){this.delay=delay;this.effects=effects;this.assertions=assertions;}
        static TestStep decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"test step","delay","effects","assertions");
            return new TestStep(boundedProvider(manager,o,"delay",0,0,120000),effects(manager,o,"effects"),o.has("assertions")?manager.decode(Assertion[].class,o.get("assertions")):new Assertion[0]);
        }
    }
    public static final class Assertion {
        final BlockPos pos; final IBlockState state; final ResourceLocation tag; final BlockMatcher matcher; final Long time; final DoubleRange timeRange; final DataRules.Test condition;
        Assertion(BlockPos pos,IBlockState state,ResourceLocation tag,BlockMatcher matcher,Long time,DoubleRange timeRange,DataRules.Test condition){this.pos=pos;this.state=state;this.tag=tag;this.matcher=matcher;this.time=time;this.timeRange=timeRange;this.condition=condition;}
        public static Assertion decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {
            JsonObject o=fields(json,"test assertion","pos","block","tag","matcher","time","time_range","condition");
            int kinds=0;for(String key:new String[]{"block","tag","matcher","time","time_range","condition"})if(o.has(key))kinds++;
            if(kinds!=1)throw new IllegalArgumentException("assertion requires exactly one block, tag, matcher, time, time_range or condition");
            boolean positioned=o.has("block")||o.has("tag")||o.has("matcher");
            if(!positioned&&o.has("pos"))throw new IllegalArgumentException("pos is only supported for block assertions; conditions use the test origin");
            return new Assertion(positioned?manager.decode(BlockPos.class,o.get("pos")):null,o.has("block")?manager.decode(IBlockState.class,o.get("block")):null,o.has("tag")?manager.decode(ResourceLocation.class,o.get("tag")):null,o.has("matcher")?manager.decode(BlockMatcher.class,o.get("matcher")):null,o.has("time")?manager.decode(Long.class,o.get("time")):null,o.has("time_range")?manager.decode(DoubleRange.class,o.get("time_range")):null,o.has("condition")?manager.decode(DataRules.Test.class,o.get("condition")):null);
        }
        boolean matches(DataContext context,CompiledTagModule tags){
            if(condition!=null)return condition.test(context);
            WorldServer world=context.getWorld();
            if(time!=null)return world.getWorldTime()==time;
            if(timeRange!=null)return timeRange.test(world.getWorldTime());
            BlockPos target=context.getOrigin().add(pos);IBlockState actual=world.getBlockState(target);
            if(state!=null)return actual.equals(state);
            if(matcher!=null)return matcher.test(world,target,actual);
            ResourceLocation actualId=Block.REGISTRY.getNameForObject(actual.getBlock());return actualId!=null&&tags.contains("block",tag,actualId);
        }
    }
    /** Retained results contain no world, player, context, or block snapshot references. */
    private static final class ScenarioResult {
        final String runId, owner;
        final ResourceLocation scenario;
        final long seed;
        final int dimension, assertionsTotal;
        BlockPos origin;
        String status = "running", message = "", cleanup = "pending";
        int ticks, attemptTicks, attempt = 1, timeout, retries, step, assertionsPassed;
        int pendingStep = -1, pendingAssertion = -1;
        ScenarioResult(String runId, String owner, ResourceLocation scenario, TestDef definition, int dimension) {
            this.runId = runId; this.owner = owner; this.scenario = scenario; this.seed = definition.seed; this.dimension = dimension;
            int total = definition.assertions.size();
            for (TestStep item : definition.sequence) total += item.assertions.length;
            assertionsTotal = total;
        }
        JsonObject snapshot() {
            JsonObject value = new JsonObject();
            value.addProperty("run_id", runId); value.addProperty("scenario", scenario.toString());
            value.addProperty("status", status); value.addProperty("message", message); value.addProperty("cleanup", cleanup);
            value.addProperty("dimension", dimension); value.addProperty("seed", seed); value.addProperty("attempt_seed", seed + attempt - 1);
            value.addProperty("ticks", ticks); value.addProperty("attempt_ticks", attemptTicks); value.addProperty("attempt", attempt);
            value.addProperty("timeout_ticks", timeout); value.addProperty("retries", retries); value.addProperty("completed_steps", step);
            value.addProperty("assertions_passed", assertionsPassed); value.addProperty("assertions_total", assertionsTotal);
            value.addProperty("pending_assertion", pendingAssertion < 0 ? null :
                    (pendingStep < 0 ? "assertions[" : "sequence[" + pendingStep + "].assertions[") + pendingAssertion + "]");
            if (origin != null) {
                JsonArray coordinates = new JsonArray(); coordinates.add(origin.getX()); coordinates.add(origin.getY()); coordinates.add(origin.getZ());
                value.add("origin", coordinates);
            }
            return value;
        }
    }
    private final class RunningTest {
        final ResourceLocation id; final TestDef def; final Environment environment; final ICommandSender sender; final BlockPos requestedOrigin;
        final ScenarioResult result;
        int completedAssertions;
        final Map<BlockPos,BlockSnapshot> original=new LinkedHashMap<>();
        final List<Environment> entered=new ArrayList<>();
        BlockPos origin; DataContext context; int age,attempt,timeout,retries,step,stepDue; long originalTime;
        final NBTTagCompound inputs=new NBTTagCompound();
        boolean placed,cancelled,stepApplied,testSetup,cleaning;
        RunningTest(ResourceLocation id,TestDef def,Environment env,ICommandSender sender,BlockPos requestedOrigin,ScenarioResult result){this.id=id;this.def=def;this.environment=env;this.sender=sender;this.requestedOrigin=requestedOrigin;this.result=result;}
        void begin(WorldServer world) {
            origin=requestedOrigin!=null?requestedOrigin:(sender.getEntityWorld()==world?sender.getPosition():world.getSpawnPoint()).add(2,0,2);
            result.origin=origin;
            try { retries=def.retries.sample(new Random(def.seed)); result.retries=retries; startAttempt(world); }
            catch(RuntimeException failure) { finish(world,false,"setup failed: "+failure.getMessage()); }
        }
        void startAttempt(WorldServer world) {
            while(!cancelled) {
                try { captureAndPlace(world);return; }
                catch(RuntimeException failure) {
                    String cleanupFailure=cleanup(world);
                    if(cleanupFailure!=null)throw new IllegalStateException("Setup cleanup failed: "+cleanupFailure,failure);
                    if(cancelled)return;
                    if(attempt>=retries)throw failure;
                    attempt++;
                }
            }
        }
        void captureAndPlace(WorldServer world) {
            original.clear();entered.clear();age=0;step=0;stepApplied=false;testSetup=false;
            completedAssertions=0; result.assertionsPassed=0; result.pendingAssertion=-1; result.pendingStep=-1; result.step=0;
            result.attempt=attempt+1; result.attemptTicks=0;
            inputs.setInteger("age",0);inputs.setInteger("attempt",attempt);inputs.setLong("seed",def.seed+attempt);
            context=new DataContext(world,null,origin,new Random(def.seed+attempt),attempt).withInputs(inputs);
            timeout=def.timeout.sample(context.getRandom()); originalTime=world.getWorldTime();
            result.timeout=timeout;
            captureEnvironment(world,environment);
            for(Assertion assertion:def.assertions)if(assertion.pos!=null)captureBlock(world,origin.add(assertion.pos));
            for(TestStep item:def.sequence)for(Assertion assertion:item.assertions)if(assertion.pos!=null)captureBlock(world,origin.add(assertion.pos));
            placed=true; placeEnvironment(world,environment);
            if(cancelled)return;
            testSetup=true; applyActive(def.setup);
            if(def.sequence.length>0)stepDue=def.sequence[0].delay.sample(context.getRandom());
            if(!cancelled)advanceSequence();
        }
        void captureEnvironment(WorldServer world,Environment env) {
            capture(world,env.size);
            if(env.structure!=null) {
                Template template=structures.findTemplate(env.structure);
                if(template==null)throw new IllegalArgumentException("Missing test structure "+env.structure);
                capture(world,template.getSize());
            }
            for(BlockChange change:env.setup)captureBlock(world,origin.add(change.pos));
            for(Environment child:env.environments)captureEnvironment(world,child);
        }
        void placeEnvironment(WorldServer world,Environment env) {
            if(cancelled)return;
            for(Environment child:env.environments)placeEnvironment(world,child);
            if(cancelled)return;
            entered.add(env);
            if(env.structure!=null)structures.findTemplate(env.structure).addBlocksToWorld(world,origin,new PlacementSettings().setIgnoreEntities(true));
            for(BlockChange change:env.setup) {
                if(cancelled)return;
                world.setBlockState(origin.add(change.pos),change.choose(context.getRandom()),3);
            }
            applyActive(env.setupEffects);
        }
        void applyActive(DataRules.Effect[] effects) {
            for(DataRules.Effect effect:effects) { if(cancelled)return;effect.apply(context,null); }
        }
        void capture(WorldServer world,BlockPos size) {
            validateSize(size);requireHeight(origin);requireHeight(origin.add(size).add(-1,-1,-1));
            for(BlockPos pos:BlockPos.getAllInBox(origin,origin.add(size).add(-1,-1,-1)))captureBlock(world,pos);
        }
        void captureBlock(WorldServer world,BlockPos pos) {
            requireHeight(pos);BlockPos immutable=pos.toImmutable();
            if(!original.containsKey(immutable)) {
                if(original.size()>=32768)throw new IllegalArgumentException("Combined test footprint exceeds 32768 blocks");
                TileEntity tile=world.getTileEntity(immutable);
                original.put(immutable,new BlockSnapshot(world.getBlockState(immutable),tile==null?null:tile.writeToNBT(new NBTTagCompound())));
            }
        }
        boolean matches(Assertion[] assertions) {
            result.assertionsPassed=completedAssertions;
            for(int i=0;i<assertions.length;i++) {
                if(cancelled)return false;
                result.pendingStep=step; result.pendingAssertion=i;
                if(!assertions[i].matches(context,tags))return false;
                result.assertionsPassed=completedAssertions+i+1;
            }
            if(cancelled)return false;
            completedAssertions+=assertions.length; result.pendingAssertion=-1;
            return true;
        }
        void advanceSequence() {
            while(!cancelled&&step<def.sequence.length&&age>=stepDue) {
                TestStep current=def.sequence[step];
                if(!stepApplied){stepApplied=true;applyActive(current.effects);}
                if(cancelled||!matches(current.assertions))return;
                step++;stepApplied=false;
                result.step=step;
                if(step<def.sequence.length)stepDue=Math.addExact(age,def.sequence[step].delay.sample(context.getRandom()));
            }
        }
        boolean tick(WorldServer world) {
            if(cancelled)return true;
            try {
                age++;result.ticks++;result.attemptTicks=age;inputs.setInteger("age",age);context=context.withInputs(inputs);advanceSequence();
                if(cancelled)return true;
                boolean passed=step==def.sequence.length;
                if(passed) {
                    result.assertionsPassed=completedAssertions;
                    for(int i=0;i<def.assertions.size();i++) {
                        if(cancelled)return true;
                        result.pendingStep=-1; result.pendingAssertion=i;
                        if(!def.assertions.get(i).matches(context,tags)){passed=false;break;}
                        result.assertionsPassed++;
                    }
                    if(passed)result.pendingAssertion=-1;
                }
                if(cancelled)return true;
                if(passed){finish(world,true,"passed in "+age+" ticks after "+(attempt+1)+" attempts");return true;}
                if(age<timeout)return false;
                return failAttempt(world,"timed out at "+age+" ticks");
            } catch(RuntimeException failure) { return failAttempt(world,"execution failed: "+failure.getMessage()); }
        }
        boolean failAttempt(WorldServer world,String reason) {
            if(cancelled)return true;
            if(attempt>=retries){finish(world,false,reason+" after "+(attempt+1)+" attempts");return true;}
            String cleanupFailure=cleanup(world);
            if(cleanupFailure!=null){finish(world,false,"cleanup failed: "+cleanupFailure);return true;}
            if(cancelled)return true;
            attempt++;
            try { startAttempt(world);return cancelled; }
            catch(RuntimeException failure){finish(world,false,"retry setup failed: "+failure.getMessage());return true;}
        }
        String cleanup(WorldServer world) {
            if(cleaning)return null;
            if(!placed){original.clear();return null;}
            placed=false;cleaning=true;
            RuntimeException failure=null;
            if(testSetup)for(DataRules.Effect effect:def.cleanup)try{effect.apply(context,null);}catch(RuntimeException error){failure=error;}
            for(int i=entered.size()-1;i>=0;i--)for(DataRules.Effect effect:entered.get(i).cleanupEffects)try{effect.apply(context,null);}catch(RuntimeException error){failure=error;}
            // Restore all states before restoring tile NBT; a test-created tile must never survive.
            for(Map.Entry<BlockPos,BlockSnapshot> entry:original.entrySet())try {
                world.removeTileEntity(entry.getKey());world.setBlockState(entry.getKey(),entry.getValue().state,2);
            }catch(RuntimeException error){failure=error;}
            for(Map.Entry<BlockPos,BlockSnapshot> entry:original.entrySet())try {
                if(entry.getValue().tileData!=null) {
                    TileEntity tile=TileEntity.create(world,entry.getValue().tileData.copy());
                    if(tile==null)throw new IllegalStateException("Could not restore tile at "+entry.getKey());
                    world.setTileEntity(entry.getKey(),tile);tile.markDirty();
                }
                world.notifyBlockUpdate(entry.getKey(),entry.getValue().state,entry.getValue().state,3);
            }catch(RuntimeException error){failure=error;}
            world.setWorldTime(originalTime);original.clear();entered.clear();testSetup=false;cleaning=false;
            if(failure!=null)result.cleanup="failed";
            return failure==null?null:failure.toString();
        }
        void cancel(WorldServer world) {
            if(cancelled)return;
            cancelled=true;String failure=cleanup(world);
            boolean restored=failure==null&&!"failed".equals(result.cleanup);
            result.status=restored?"cancelled":"failed";
            result.cleanup=restored?"restored":"failed";
            result.message=restored?"Scenario cancelled":"Cancellation cleanup failed: "+failure;
            if(failure!=null)sender.sendMessage(new net.minecraft.util.text.TextComponentString("Test "+id+" cleanup failed: "+failure));
        }
        void finish(WorldServer world,boolean passed,String message) {
            if(cancelled)return;
            cancelled=true;String failure=cleanup(world);
            if(failure!=null){passed=false;message+="; cleanup failed: "+failure;}
            if("failed".equals(result.cleanup))passed=false;
            result.status=passed?"passed":"failed";
            if(!"failed".equals(result.cleanup))result.cleanup=failure==null?"restored":"failed";
            result.message=message.length()>4096?message.substring(0,4096):message;
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("Test "+id+" "+(passed?"PASSED":"FAILED")+": "+message));
        }
    }
    private static final class BlockSnapshot {
        final IBlockState state;
        final NBTTagCompound tileData;
        BlockSnapshot(IBlockState state,NBTTagCompound tileData){this.state=state;this.tileData=tileData;}
    }
}
