package rml.loader.content.time;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private boolean initialized;
    private DeserializerManager deserializers;

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
            if (!clockDimensions.add(entry.getValue().dimension)) throw new IllegalArgumentException(source("world_clock",entry.getKey())+": multiple world_clock definitions target dimension "+entry.getValue().dimension);
        }
        for (Map.Entry<ResourceLocation, TimelineDef> entry : parsedTimelines.entrySet()) {
            if (!parsedClocks.containsKey(entry.getValue().clock)) throw new IllegalArgumentException(source("timeline",entry.getKey())+": timeline "+entry.getKey()+" references missing world_clock "+entry.getValue().clock);
        }
        for (Map.Entry<ResourceLocation, TestDef> entry : parsedTests.entrySet()) {
            if (!parsedEnvironments.containsKey(entry.getValue().environment)) throw new IllegalArgumentException(source("test_instance",entry.getKey())+": test_instance "+entry.getKey()+" references missing test_environment "+entry.getValue().environment);
        }
        clocks.putAll(parsedClocks); timelines.putAll(parsedTimelines); environments.putAll(parsedEnvironments); tests.putAll(parsedTests);
        MinecraftForge.EVENT_BUS.register(this);
        initialized = true;
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
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
        if(!clock.configured) { clock.time=definition.initial; clock.rate=definition.rate; clock.paused=definition.paused; clock.configured=true; world.setWorldTime(clock.time); data.markDirty(); }
        switch (args[1]) {
            case "query": sender.sendMessage(new net.minecraft.util.text.TextComponentString(id + " time=" + clock.time + " rate=" + clock.rate + " paused=" + clock.paused)); return;
            case "pause": clock.paused=true; break;
            case "resume": clock.paused=false; break;
            case "rate": { if (args.length < 4) throw new WrongUsageException("clock rate <id> <number>"); double rate=number(args[3]); if (rate < 0 || rate > 1000) throw new CommandException("Clock rate must be between 0 and 1000"); clock.rate=rate; clock.fraction=0; break; }
            case "set": if (args.length < 4) throw new WrongUsageException("clock set <id> <ticks>"); clock.time=longNumber(args[3]); clock.fraction=0; world.setWorldTime(clock.time); break;
            case "add": if (args.length < 4) throw new WrongUsageException("clock add <id> <ticks>"); clock.time+=longNumber(args[3]); clock.fraction=0; world.setWorldTime(clock.time); break;
            default: throw new WrongUsageException("clock <query|pause|resume|rate|set|add> <id> [value]");
        }
        data.markDirty(); sender.sendMessage(new net.minecraft.util.text.TextComponentString("Clock " + id + " updated"));
    }

    private void executeTimeline(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !"query".equals(args[1])) throw new WrongUsageException("timeline query <id>");
        ResourceLocation id=id(args[2]); TimelineDef def=require(timelines,id,"timeline");
        WorldServer world=sender.getEntityWorld() instanceof WorldServer?(WorldServer)sender.getEntityWorld():server.getWorld(0);
        TimeWorldData.Timeline state=TimeWorldData.get(world).timelines.get(id);
        sender.sendMessage(new net.minecraft.util.text.TextComponentString(id+" clock="+def.clock+" event="+(state==null?0:state.eventIndex)+" cycle="+(state==null?0:state.cycle)));
    }

    private void executeTest(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if ((args.length != 3 && args.length != 6) || !"run".equals(args[1])) throw new WrongUsageException("test run <id> [x y z]");
        ResourceLocation id=id(args[2]); TestDef def=require(tests,id,"test_instance");
        BlockPos requestedOrigin=args.length==6?new BlockPos(commandInteger(args[3]),commandInteger(args[4]),commandInteger(args[5])):null;
        WorldServer world=sender.getEntityWorld() instanceof WorldServer?(WorldServer)sender.getEntityWorld():server.getWorld(0);
        if (world == null) throw new CommandException("No server world is available");
        List<RunningTest> list=running.get(world);
        if(list!=null) for (RunningTest current:list) if (current.id.equals(id)) throw new CommandException("Test is already running: "+id);
        RunningTest run=new RunningTest(id,def,environments.get(def.environment),sender,requestedOrigin);
        run.begin(world);
        running.computeIfAbsent(world,key->new ArrayList<>()).add(run);
        sender.sendMessage(new net.minecraft.util.text.TextComponentString("Started test " + id + " in dimension " + world.provider.getDimension()));
    }

    @SubscribeEvent public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        WorldServer world=(WorldServer)event.world;
        TimeWorldData data=TimeWorldData.get(world);
        boolean dirty=false;
        for(Map.Entry<ResourceLocation,ClockDef> entry:clocks.entrySet()) {
            if(entry.getValue().dimension!=world.provider.getDimension()) continue;
            TimeWorldData.Clock clock=clock(data,entry.getKey());
            if (!clock.configured) { ClockDef def=entry.getValue(); clock.time=def.initial; clock.rate=def.rate; clock.paused=def.paused; clock.configured=true; dirty=true; }
            if(!clock.paused) { double scaled=clock.rate+clock.fraction; long advance=(long)Math.floor(scaled); clock.fraction=scaled-advance; if(advance!=0)clock.time+=advance; if(clock.rate!=0)dirty=true; }
            world.setWorldTime(clock.time);
        }
        for(Map.Entry<ResourceLocation,TimelineDef> entry:timelines.entrySet()) {
            ClockDef clock=clocks.get(entry.getValue().clock);
            if(clock!=null && clock.dimension==world.provider.getDimension()) dirty |= tickTimeline(world,data,entry.getKey(),entry.getValue());
        }
        if(dirty) data.markDirty();
        List<RunningTest> active=running.get(world);
        if(active!=null) { for(int i=active.size()-1;i>=0;i--) if(active.get(i).tick(world)) active.remove(i); if(active.isEmpty()) running.remove(world); }
    }

    private boolean tickTimeline(WorldServer world, TimeWorldData data, ResourceLocation id, TimelineDef def) {
        TimeWorldData.Clock clock=clock(data,def.clock); TimeWorldData.Timeline state=data.timelines.computeIfAbsent(id,key->new TimeWorldData.Timeline());
        long now=clock.time;
        if(state.completed) return false;
        boolean changed=false;
        if(!state.started) { state.previous=now; state.lastClock=now; state.started=true; changed=true; }
        else if(now<state.lastClock) { state.previous=now; state.lastClock=now; state.cycle=0; state.eventIndex=0; state.completed=false; changed=true; }
        else state.lastClock=now;
        int dispatched=0;
        while(dispatched<4096 && !state.completed) {
            if(state.eventIndex>=def.events.size()) {
                if(!def.loop) { state.completed=true; changed=true; break; }
                try { state.previous=Math.addExact(state.previous,def.duration); state.cycle=Math.addExact(state.cycle,1); }
                catch(ArithmeticException overflow) { state.completed=true; changed=true; world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString("Timeline "+id+" exceeded its clock range and stopped")); break; }
                state.eventIndex=0;
            }
            TimelineEvent event=def.events.get(state.eventIndex);
            long due;
            try { due=Math.addExact(state.previous,event.at); }
            catch(ArithmeticException overflow) { state.completed=true; changed=true; world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString("Timeline "+id+" exceeded its clock range and stopped")); break; }
            if(due>now) break;
            state.eventIndex++; dispatched++; changed=true;
            if(event.function!=null) { try { rules.invoke(event.function,new DataContext(world,null,event.origin(world),world.rand,0)); } catch(RuntimeException failure) { world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString("Timeline "+id+" function "+event.function+" failed: "+failure.getMessage())); } }
            if(event.setTime!=null) { clock.time=event.setTime; clock.fraction=0; world.setWorldTime(clock.time); }
            for(BlockChange block:event.blocks) world.setBlockState(event.origin(world).add(block.pos),block.choose(world.rand),3);
            if(event.message!=null) world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString(event.message));
        }
        return changed;
    }

    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event) {
        if(event.getWorld() instanceof WorldServer) {
            WorldServer world=(WorldServer)event.getWorld();
            List<RunningTest> active=running.remove(world);
            if(active!=null) for(RunningTest test:active) test.cleanup(world);
        }
    }
    public void stop() {
        for(Map.Entry<WorldServer,List<RunningTest>> entry:running.entrySet()) for(RunningTest test:entry.getValue()) test.cleanup(entry.getKey());
        running.clear();
    }

    private String source(String type,ResourceLocation id){CompiledResource resource=catalog.snapshot().resources(type).get(id);return resource==null?id.toString():resource.getSource();}
    private static TimeWorldData.Clock clock(TimeWorldData data,ResourceLocation id) { return data.clocks.computeIfAbsent(id,key->new TimeWorldData.Clock()); }
    private static <T>T require(Map<ResourceLocation,T> map,ResourceLocation id,String type)throws CommandException { T found=map.get(id); if(found==null)throw new CommandException("Unknown "+type+": "+id); return found; }
    private static ResourceLocation id(String value)throws CommandException { try{return new ResourceLocation(value);}catch(RuntimeException e){throw new CommandException("Invalid resource identifier: "+value);} }
    private static JsonObject object(JsonElement e,String label){if(!e.isJsonObject())throw new IllegalArgumentException(label+" must be an object");return e.getAsJsonObject();}
    private static int commandInteger(String value)throws CommandException {try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new CommandException("Expected integer coordinate: "+value);}}
    private static long longNumber(String value)throws CommandException {try{return Long.parseLong(value);}catch(NumberFormatException e){throw new CommandException("Expected integer: "+value);}}
    private static double number(String value)throws CommandException {try{double n=Double.parseDouble(value);if(Double.isNaN(n)||Double.isInfinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new CommandException("Expected finite number: "+value);}}

    public static final class ClockDef {
        public final long initial; public final double rate; public final boolean paused; public final int dimension;
        public ClockDef(long initial,double rate,boolean paused,int dimension){this.initial=initial;this.rate=rate;this.paused=paused;this.dimension=dimension;}
        public static ClockDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException { JsonObject o=object(json,"world_clock");double rate=o.has("rate")?manager.decode(Double.class,o.get("rate")):1.0;if(!Double.isFinite(rate)||rate<0||rate>1000)throw new IllegalArgumentException("rate must be from 0 to 1000");return new ClockDef(o.has("initial_time")?manager.decode(Long.class,o.get("initial_time")):0,rate,o.has("paused")&&manager.decode(Boolean.class,o.get("paused")),o.has("dimension")?manager.decode(Integer.class,o.get("dimension")):0); }
    }
    public static final class TimelineDef {
        public final ResourceLocation clock; public final boolean loop; public final List<TimelineEvent> events; public final long duration;
        public TimelineDef(ResourceLocation clock,boolean loop,List<TimelineEvent> events,long duration){this.clock=clock;this.loop=loop;this.events=events;this.duration=duration;}
        public static TimelineDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {JsonObject o=object(json,"timeline");ResourceLocation clock=new ResourceLocation(manager.decode(String.class,o.get("clock")));boolean loop=!o.has("loop")||manager.decode(Boolean.class,o.get("loop"));TimelineEvent[] decoded=manager.decode(TimelineEvent[].class,o.get("events"));if(decoded.length==0)throw new IllegalArgumentException("events must not be empty");List<TimelineEvent> events=java.util.Arrays.asList(decoded);long end=-1;for(TimelineEvent event:events){if(event.at<0||event.at<end)throw new IllegalArgumentException("event times must be nonnegative and ordered");end=event.at;}return new TimelineDef(clock,loop,Collections.unmodifiableList(events),Math.addExact(end,1));}
    }
    public static final class TimelineEvent {
        public final long at; public final ResourceLocation function; public final BlockPos offset; public final Long setTime; public final String message; public final List<BlockChange> blocks;
        public TimelineEvent(long at,ResourceLocation function,BlockPos offset,Long setTime,String message,List<BlockChange> blocks){this.at=at;this.function=function;this.offset=offset;this.setTime=setTime;this.message=message;this.blocks=blocks;}
        public static TimelineEvent decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {JsonObject o=object(json,"timeline event");ResourceLocation function=o.has("function")?new ResourceLocation(manager.decode(String.class,o.get("function"))):null;BlockPos offset=o.has("origin")?manager.decode(BlockPos.class,o.get("origin")):BlockPos.ORIGIN;Long set=o.has("set_time")?manager.decode(Long.class,o.get("set_time")):null;String message=o.has("message")?manager.decode(String.class,o.get("message")):null;BlockChange[] blocks=o.has("blocks")?manager.decode(BlockChange[].class,o.get("blocks")):new BlockChange[0];return new TimelineEvent(manager.decode(Long.class,o.get("at")),function,offset,set,message,java.util.Arrays.asList(blocks));}
        BlockPos origin(WorldServer world){return world.getSpawnPoint().add(offset);}
    }
    public static final class BlockChange {
        public final BlockPos pos; public final IBlockState state; public final WeightedList<IBlockState> states;
        public BlockChange(BlockPos pos,IBlockState state){this(pos,state,null);}
        public BlockChange(BlockPos pos,WeightedList<IBlockState> states){this(pos,null,states);}
        private BlockChange(BlockPos pos,IBlockState state,WeightedList<IBlockState> states){this.pos=pos;this.state=state;this.states=states;}
        public static BlockChange decode(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
            JsonObject value = object(json, "block change");
            if (value.has("state") == value.has("states")) throw new IllegalArgumentException("block change requires exactly one of state or states");
            BlockPos position = manager.decode(BlockPos.class, value.get("pos"));
            if (value.has("state")) return new BlockChange(position, manager.decode(IBlockState.class, value.get("state")));
            Type stateType = new TypeToken<WeightedList<IBlockState>>() { }.getType();
            return new BlockChange(position, manager.<WeightedList<IBlockState>>decode(stateType, value.get("states")));
        }
        IBlockState choose(Random random){return states==null?state:states.sample(random);}
    }
    public static final class Environment {
        public final ResourceLocation structure; public final List<BlockChange> setup; public final BlockPos size;
        public Environment(ResourceLocation structure,List<BlockChange> setup,BlockPos size){this.structure=structure;this.setup=setup;this.size=size;}
        public static Environment decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {JsonObject o=object(json,"test_environment");ResourceLocation structure=o.has("structure")?new ResourceLocation(manager.decode(String.class,o.get("structure"))):null;BlockChange[] setup=o.has("setup")?manager.decode(BlockChange[].class,o.get("setup")):new BlockChange[0];BlockPos size=o.has("size")?manager.decode(BlockPos.class,o.get("size")):new BlockPos(1,1,1);long volume=(long)size.getX()*size.getY()*size.getZ();if(size.getX()<1||size.getY()<1||size.getZ()<1||volume>32768)throw new IllegalArgumentException("size must contain 1 to 32768 blocks");if(structure==null&&setup.length==0)throw new IllegalArgumentException("environment requires a structure or at least one setup block");return new Environment(structure,java.util.Arrays.asList(setup),size);}
    }
    public static final class TestDef {
        public final ResourceLocation environment; public final int timeout,retries; public final List<Assertion> assertions;
        public TestDef(ResourceLocation environment,int timeout,int retries,List<Assertion> assertions){this.environment=environment;this.timeout=timeout;this.retries=retries;this.assertions=assertions;}
        public static TestDef decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {JsonObject o=object(json,"test_instance");ResourceLocation env=new ResourceLocation(manager.decode(String.class,o.get("environment")));int timeout=o.has("timeout")?manager.decode(Integer.class,o.get("timeout")):200,retries=o.has("retries")?manager.decode(Integer.class,o.get("retries")):0;if(timeout<1||timeout>120000||retries<0||retries>100)throw new IllegalArgumentException("timeout/retries out of range");Assertion[] assertions=manager.decode(Assertion[].class,o.get("assertions"));if(assertions.length==0)throw new IllegalArgumentException("at least one assertion is required");return new TestDef(env,timeout,retries,Collections.unmodifiableList(java.util.Arrays.asList(assertions)));}
    }
    public static final class Assertion {
        public final BlockPos pos; public final IBlockState state; public final ResourceLocation tag; public final BlockMatcher matcher; public final Long time; public final DoubleRange timeRange;
        public Assertion(BlockPos pos,IBlockState state,ResourceLocation tag,BlockMatcher matcher,Long time,DoubleRange timeRange){this.pos=pos;this.state=state;this.tag=tag;this.matcher=matcher;this.time=time;this.timeRange=timeRange;}
        public static Assertion decode(DeserializerManager manager,JsonElement json)throws JsonDeserializeException {JsonObject o=object(json,"test assertion");int kinds=(o.has("block")?1:0)+(o.has("tag")?1:0)+(o.has("matcher")?1:0)+(o.has("time")?1:0)+(o.has("time_range")?1:0);if(kinds!=1)throw new IllegalArgumentException("assertion requires exactly one of block, tag, matcher, time, or time_range");if(o.has("block"))return new Assertion(manager.decode(BlockPos.class,o.get("pos")),manager.decode(IBlockState.class,o.get("block")),null,null,null,null);if(o.has("tag"))return new Assertion(manager.decode(BlockPos.class,o.get("pos")),null,new ResourceLocation(manager.decode(String.class,o.get("tag"))),null,null,null);if(o.has("matcher"))return new Assertion(manager.decode(BlockPos.class,o.get("pos")),null,null,manager.decode(BlockMatcher.class,o.get("matcher")),null,null);if(o.has("time"))return new Assertion(null,null,null,null,manager.decode(Long.class,o.get("time")),null);return new Assertion(null,null,null,null,null,manager.decode(DoubleRange.class,o.get("time_range")));}
        boolean matches(WorldServer world,BlockPos origin,CompiledTagModule tags){if(time!=null)return world.getWorldTime()==time;if(timeRange!=null)return timeRange.test(world.getWorldTime());BlockPos target=origin.add(pos);IBlockState actual=world.getBlockState(target);if(state!=null)return actual.equals(state);if(matcher!=null)return matcher.test(world,target,actual);ResourceLocation actualId=Block.REGISTRY.getNameForObject(actual.getBlock());return actualId!=null&&tags.contains("block",tag,actualId);}
    }
    private final class RunningTest {
        final ResourceLocation id;final TestDef def;final Environment environment;final ICommandSender sender;final BlockPos requestedOrigin;final Map<BlockPos,BlockSnapshot> original=new LinkedHashMap<>();BlockPos origin;int age,attempt;boolean placed;
        RunningTest(ResourceLocation id,TestDef def,Environment env,ICommandSender sender,BlockPos requestedOrigin){this.id=id;this.def=def;this.environment=env;this.sender=sender;this.requestedOrigin=requestedOrigin;}
        void begin(WorldServer world){origin=requestedOrigin!=null?requestedOrigin:(sender.getEntityWorld()==world?sender.getPosition():world.getSpawnPoint()).add(2,0,2);try{captureAndPlace(world);}catch(RuntimeException failure){cleanup(world);throw failure;}}
        void captureAndPlace(WorldServer world){original.clear();Template template=environment.structure==null?null:structures.findTemplate(environment.structure);if(environment.structure!=null&&template==null)throw new IllegalArgumentException("test environment references missing structure "+environment.structure);capture(world,template==null?environment.size:template.getSize());for(BlockChange change:environment.setup)captureBlock(world,origin.add(change.pos));for(Assertion assertion:def.assertions)if(assertion.pos!=null)captureBlock(world,origin.add(assertion.pos));placed=true;if(template!=null)template.addBlocksToWorld(world,origin,new PlacementSettings());for(BlockChange change:environment.setup)world.setBlockState(origin.add(change.pos),change.choose(world.rand),3);age=0;}
        void capture(WorldServer world,BlockPos size){long volume=(long)size.getX()*size.getY()*size.getZ();if(size.getX()<1||size.getY()<1||size.getZ()<1||volume>32768)throw new IllegalArgumentException("test environment footprint must contain 1 to 32768 blocks");for(BlockPos p:BlockPos.getAllInBox(origin,origin.add(size).subtract(new BlockPos(1,1,1))))captureBlock(world,p);}
        void captureBlock(WorldServer world,BlockPos pos){BlockPos immutable=pos.toImmutable();if(!original.containsKey(immutable)){TileEntity tile=world.getTileEntity(immutable);NBTTagCompound tileData=tile==null?null:tile.writeToNBT(new NBTTagCompound());original.put(immutable,new BlockSnapshot(world.getBlockState(immutable),tileData));}}
        boolean tick(WorldServer world){age++;for(Assertion assertion:def.assertions){try{if(assertion.matches(world,origin,tags))continue;}catch(RuntimeException failure){finish(world,false,"assertion evaluation failed: "+failure.getMessage());return true;}if(age<def.timeout)return false;if(attempt<def.retries){attempt++;cleanup(world);try{captureAndPlace(world);}catch(RuntimeException failure){finish(world,false,"retry setup failed after "+attempt+" retries: "+failure.getMessage());return true;}return false;}finish(world,false,"timed out after "+(attempt+1)+" attempts ("+attempt+" retries)");return true;}finish(world,true,"passed in "+age+" ticks after "+(attempt+1)+" attempts");return true;}
        void cleanup(WorldServer world){if(placed)for(Map.Entry<BlockPos,BlockSnapshot> e:original.entrySet()){BlockPos pos=e.getKey();BlockSnapshot snapshot=e.getValue();world.setBlockState(pos,snapshot.state,3);if(snapshot.tileData!=null){TileEntity tile=world.getTileEntity(pos);if(tile!=null){tile.readFromNBT(snapshot.tileData);tile.markDirty();}}}placed=false;original.clear();}
        void finish(WorldServer world,boolean passed,String result){cleanup(world);String text="Test "+id+" "+(passed?"PASSED":"FAILED")+": "+result;world.getMinecraftServer().getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString(text));}
    }
    private static final class BlockSnapshot {
        final IBlockState state;
        final NBTTagCompound tileData;
        BlockSnapshot(IBlockState state,NBTTagCompound tileData){this.state=state;this.tileData=tileData;}
    }
}
