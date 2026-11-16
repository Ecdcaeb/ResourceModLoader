package rml.loader.content.time;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.loader.content.CompiledResource;
import rml.loader.content.CompiledStructureModule;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import rml.loader.deserialize.NativeTypeDecoders;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.UUID;

/** Standalone composition checks and an explicit real-server integration entrypoint. */
public final class TimeCompositionContractTest {
    private static final ResourceLocation CLOCK = new ResourceLocation("contract", "clock");
    private static final ResourceLocation TIMELINE = new ResourceLocation("contract", "timeline");

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Fixture fixture = new Fixture((id, context) -> { throw new AssertionError("Unexpected function " + id); });
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "offset"), DataRules.NumberValue.class,
                json -> context -> context.getLevel() + 0.25));
        TimeTestModule.TimeOffset offset = fixture.manager.decode(TimeTestModule.TimeOffset.class,
                new JsonParser().parse("{\"ticks\":{\"type\":\"rml:add\",\"inputs\":[{\"type\":\"contract:offset\"},2]},\"days\":1,\"cycles\":2}"));
        check(offset.evaluate(new DataContext(null, null, null, new Random(7), 3), 10) == 24026,
                "Nested custom numbers, day/cycle conversion and upward tick rounding must compose");
        TimeTestModule.TimelineDef timeline = fixture.manager.decode(TimeTestModule.TimelineDef.class, new JsonParser().parse(
                "{\"clock\":\"contract:clock\",\"cycle_length\":10,\"duration\":{\"days\":2},\"events\":[{\"at\":0},{\"at\":{\"days\":1,\"cycles\":2}}]}"));
        TimeWorldData.Timeline cursor = new TimeWorldData.Timeline();
        timeline.schedule(cursor, new DataContext(null, null, null, new Random(7), 0));
        check(cursor.offsets[0] == 0 && cursor.offsets[1] == 24020 && cursor.duration == 48000,
                "Timeline schedule must preserve explicit day/cycle boundaries");
        reject(fixture, TimeTestModule.ClockDef.class, "{\"rate\":0.5,\"unsupported\":true}");
        reject(fixture, TimeTestModule.Environment.class, "{\"size\":[1,257,1]}");
        reject(fixture, TimeTestModule.TestDef.class, "{\"environment\":\"contract:env\",\"timeout\":null,\"assertions\":[{\"time\":0}]}");
        System.out.println("TimeCompositionContractTest standalone checks passed; verifyInWorld requires a real server world");
    }

    /** Invoke synchronously on the server thread with a sender in this world and a loaded, safe test origin. */
    public static void verifyInWorld(WorldServer world, ICommandSender sender, BlockPos origin) throws Exception {
        check(sender.getEntityWorld() == world, "Sender must belong to the tested world");
        check(origin.getY() >= 0 && origin.getY() <= 255, "Native test origin height");
        IBlockState before = world.getBlockState(origin);
        TileEntity oldTile = world.getTileEntity(origin);
        NBTTagCompound beforeTile = oldTile == null ? null : oldTile.writeToNBT(new NBTTagCompound());
        long beforeTime = world.getWorldTime();
        TimeWorldData data = TimeWorldData.get(world);
        NBTTagCompound beforeClocks = data.writeToNBT(new NBTTagCompound());
        List<Integer> samples = new ArrayList<>();
        int[] effects = {0}, cleanups = {0}, functions = {0}, timelineEffects = {0};
        Fixture fixture = new Fixture((id, context) -> {
            check(id.equals(new ResourceLocation("contract", "gold")), "Unexpected function dispatch");
            functions[0]++;
            BlockPos pos = context.getOrigin();
            return world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(),
                    "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:gold_block");
        });
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "place"), DataRules.Effect.class,
                json -> (context, target) -> {
                    effects[0]++;
                    samples.add(context.getRandom().nextInt());
                    context.getWorld().removeTileEntity(context.getOrigin());
                    context.getWorld().setBlockState(context.getOrigin(), Blocks.GOLD_BLOCK.getDefaultState(), 2);
                }));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "cleanup"), DataRules.Effect.class,
                json -> (context, target) -> cleanups[0]++));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "retry"), DataRules.Test.class,
                json -> context -> context.getLevel() == 1 && context.getWorld().getBlockState(context.getOrigin()).getBlock() == Blocks.GOLD_BLOCK));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "never"), DataRules.Test.class,
                json -> context -> false));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "boundary"), DataRules.Test.class,
                json -> context -> context.getWorld().getWorldTime() == 2 && context.getInputs().getLong("scheduled_time") == 2));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "after_function"), DataRules.Effect.class,
                json -> (context, target) -> {
                    check(context.getWorld().getBlockState(context.getOrigin()).getBlock() == Blocks.GOLD_BLOCK, "Native function must execute before the event effect");
                    timelineEffects[0]++;
                    context.getWorld().setBlockState(context.getOrigin(), Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
                }));
        fixture.manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "throw_effect"), DataRules.Effect.class,
                json -> (context, target) -> { throw new IllegalStateException(); }));
        BlockPos offset = origin.subtract(world.getSpawnPoint());
        fixture.resource("world_clock", "clock", "{\"initial_time\":0,\"rate\":0.5,\"dimension\":" + world.provider.getDimension() + "}");
        fixture.resource("timeline", "timeline", "{\"clock\":\"contract:clock\",\"loop\":false,\"events\":[{\"at\":{\"ticks\":{\"type\":\"rml:add\",\"inputs\":[1,1]}},\"origin\":[" + offset.getX() + "," + offset.getY() + "," + offset.getZ() + "],\"condition\":{\"type\":\"contract:boundary\"},\"function\":\"contract:gold\",\"effects\":[{\"type\":\"contract:after_function\"}]}]}");
        fixture.resource("test_environment", "env", "{\"size\":[1,1,1],\"environments\":[{\"size\":[1,1,1],\"cleanup_effects\":[{\"type\":\"contract:cleanup\"}]}]}");
        fixture.resource("test_instance", "retry", "{\"environment\":\"contract:env\",\"seed\":73,\"timeout\":2,\"retries\":1,\"sequence\":[{\"delay\":1,\"effects\":[{\"type\":\"contract:place\"}],\"assertions\":[{\"condition\":{\"type\":\"contract:retry\"}}]}]}");
        fixture.resource("test_instance", "fail", "{\"environment\":\"contract:env\",\"seed\":73,\"timeout\":1,\"setup\":[{\"type\":\"contract:place\"}],\"assertions\":[{\"condition\":{\"type\":\"contract:never\"}}]}");
        fixture.resource("test_instance", "cancel", "{\"environment\":\"contract:env\",\"timeout\":20,\"sequence\":[{\"delay\":2,\"effects\":[{\"type\":\"contract:place\"}]}],\"assertions\":[{\"condition\":{\"type\":\"contract:never\"}}]}");
        fixture.resource("test_instance", "setup_failure", "{\"environment\":\"contract:env\",\"setup\":[{\"type\":\"contract:place\"},{\"type\":\"contract:throw_effect\"}],\"assertions\":[{\"pos\":[0,0,0],\"block\":\"minecraft:gold_block\"}]}");
        fixture.resource("test_instance", "cleanup_failure", "{\"environment\":\"contract:env\",\"setup\":[{\"type\":\"contract:place\"}],\"cleanup\":[{\"type\":\"contract:throw_effect\"}],\"assertions\":[{\"pos\":[0,0,0],\"block\":\"minecraft:gold_block\"}]}");
        fixture.resource("test_instance", "cleanup_retry_failure", "{\"environment\":\"contract:env\",\"timeout\":1,\"retries\":1,\"setup\":[{\"type\":\"contract:place\"}],\"cleanup\":[{\"type\":\"contract:throw_effect\"}],\"assertions\":[{\"condition\":{\"type\":\"contract:never\"}}]}");
        try {
            fixture.publish(); fixture.module.initialize();
            tick(fixture.module, world);
            check(data.clocks.get(CLOCK).time == 0 && data.clocks.get(CLOCK).fraction == 0.5, "Half-rate first tick");
            DataRules.NumberValue clockValue = fixture.manager.decode(DataRules.NumberValue.class, new JsonParser().parse("{\"type\":\"rml:clock_time\",\"clock\":\"contract:clock\"}"));
            check(clockValue.evaluate(new DataContext(world, null, origin, new Random(0), 0)) == 0.5, "Clock values must expose persisted fractional progress to shared expressions");
            fixture.module.execute(world.getMinecraftServer(), sender, new String[]{"clock", "pause", CLOCK.toString()});
            tick(fixture.module, world);
            check(functions[0] == 0 && data.clocks.get(CLOCK).fraction == 0.5, "Pause retains fraction and dispatches no early function");
            NBTTagCompound saved = data.writeToNBT(new NBTTagCompound()); data.readFromNBT(saved);
            fixture.module.execute(world.getMinecraftServer(), sender, new String[]{"clock", "resume", CLOCK.toString()});
            tick(fixture.module, world); tick(fixture.module, world); tick(fixture.module, world);
            check(functions[0] == 1 && timelineEffects[0] == 1 && world.getBlockState(origin).getBlock() == Blocks.DIAMOND_BLOCK, "NumberValue boundary, Test, native function and Effect must compose in order");
            fixture.module.execute(world.getMinecraftServer(), sender, new String[]{"clock", "set", CLOCK.toString(), "0"});
            for (int i = 0; i < 6; i++) tick(fixture.module, world);
            check(functions[0] == 1, "Clock rewind must not replay consumed events");
            check(data.timelines.get(TIMELINE).completed, "One-shot timeline completes");

            world.removeTileEntity(origin); world.setBlockState(origin, Blocks.CHEST.getDefaultState(), 2);
            TileEntityChest chest = (TileEntityChest) world.getTileEntity(origin);
            chest.setInventorySlotContents(4, new ItemStack(Items.DIAMOND, 17));
            NBTTagCompound chestBefore = chest.writeToNBT(new NBTTagCompound());
            JsonObject retry = run(fixture.module, world, sender, origin, "retry");
            try {
                fixture.module.cancelScenario(world.getMinecraftServer(), "different-owner", retry.get("run_id").getAsString());
                throw new AssertionError("Another owner must not cancel an active scenario");
            } catch (net.minecraft.command.CommandException expected) { }
            tick(fixture.module, world); tick(fixture.module, world); tick(fixture.module, world);
            check(effects[0] == 2 && cleanups[0] == 2, "Timeout retries apply each step and cleanup exactly once per attempt");
            check(samples.get(0) == new Random(73).nextInt() && samples.get(1) == new Random(74).nextInt(), "Attempt seeds must reproduce 73 then 74");
            check(chestBefore.equals(world.getTileEntity(origin).writeToNBT(new NBTTagCompound())), "Passing retry must restore native chest inventory NBT");
            JsonObject retried = status(fixture.module, world, retry);
            check("running".equals(retry.get("status").getAsString()) && "passed".equals(retried.get("status").getAsString()), "Initial snapshots must not masquerade as terminal results");
            check(retried.get("attempt").getAsInt() == 2 && retried.get("attempt_seed").getAsLong() == 74 && retried.get("assertions_passed").getAsInt() == 1,
                    "Scenario report must preserve actual retry seed and completed assertions");
            check("restored".equals(retried.get("cleanup").getAsString()), "Pass is published after snapshot cleanup");
            JsonObject failed = run(fixture.module, world, sender, origin, "fail"); tick(fixture.module, world);
            failed = status(fixture.module, world, failed);
            check("failed".equals(failed.get("status").getAsString()) && "assertions[0]".equals(failed.get("pending_assertion").getAsString()), "Timeout reports the unsatisfied assertion");
            check(cleanups[0] == 3 && chestBefore.equals(world.getTileEntity(origin).writeToNBT(new NBTTagCompound())), "Failed test must restore tile NBT and execute cleanup");
            int count = effects[0];
            JsonObject stopped = run(fixture.module, world, sender, origin, "cancel"); fixture.module.stop();
            check("cancelled".equals(status(fixture.module, world, stopped).get("status").getAsString()), "Stopping the module publishes cancellation");
            for (int i = 0; i < 3; i++) tick(fixture.module, world);
            check(effects[0] == count && cleanups[0] == 4, "Stop cancels delayed steps and cleans once");
            fixture.module.initialize(); JsonObject unloaded = run(fixture.module, world, sender, origin, "cancel");
            fixture.module.onWorldUnload(new WorldEvent.Unload(world));
            for (int i = 0; i < 3; i++) tick(fixture.module, world);
            check(effects[0] == count && cleanups[0] == 5, "Unload cancels delayed steps and ignores late ticks");
            check(chestBefore.equals(world.getTileEntity(origin).writeToNBT(new NBTTagCompound())), "Cancellation must restore native tile data");
            check("cancelled".equals(status(fixture.module, world, unloaded).get("status").getAsString()), "World unload publishes cancellation");
            fixture.module.onWorldLoad(new WorldEvent.Load(world));
            JsonObject cancelled = run(fixture.module, world, sender, origin, "cancel");
            int beforeCancel = cleanups[0];
            cancelled = fixture.module.cancelScenario(world.getMinecraftServer(), "contract", cancelled.get("run_id").getAsString());
            check("cancelled".equals(cancelled.get("status").getAsString()) && "restored".equals(cancelled.get("cleanup").getAsString()), "Explicit cancellation returns completed cleanup");
            fixture.module.cancelScenario(world.getMinecraftServer(), "contract", cancelled.get("run_id").getAsString());
            check(cleanups[0] == beforeCancel + 1, "Cancelling a completed scenario must not repeat cleanup");
            JsonObject setupFailed = run(fixture.module, world, sender, origin, "setup_failure");
            check("failed".equals(setupFailed.get("status").getAsString()) && "restored".equals(setupFailed.get("cleanup").getAsString()), "Setup failure is a terminal result with restored blocks");
            JsonObject cleanupFailed = run(fixture.module, world, sender, origin, "cleanup_failure"); tick(fixture.module, world);
            cleanupFailed = status(fixture.module, world, cleanupFailed);
            check("failed".equals(cleanupFailed.get("status").getAsString()) && "failed".equals(cleanupFailed.get("cleanup").getAsString()), "Passing assertions cannot conceal a cleanup failure");
            check(chestBefore.equals(world.getTileEntity(origin).writeToNBT(new NBTTagCompound())), "Failed effects must still restore the captured inventory");
            JsonObject cleanupRetry = run(fixture.module, world, sender, origin, "cleanup_retry_failure"); tick(fixture.module, world);
            cleanupRetry = status(fixture.module, world, cleanupRetry);
            check("failed".equals(cleanupRetry.get("status").getAsString()) && cleanupRetry.get("attempt").getAsInt() == 1
                    && chestBefore.equals(world.getTileEntity(origin).writeToNBT(new NBTTagCompound())),
                    "Cleanup failure without a message must stop retries and restore the captured inventory");
        } finally {
            fixture.module.stop(); data.readFromNBT(beforeClocks); data.markDirty();
            world.removeTileEntity(origin); world.setBlockState(origin, before, 2);
            if (beforeTile != null) world.setTileEntity(origin, TileEntity.create(world, beforeTile));
            world.setWorldTime(beforeTime);
        }
    }

    private static JsonObject run(TimeTestModule module, WorldServer world, ICommandSender sender, BlockPos origin, String name) throws Exception {
        return module.startScenario(world.getMinecraftServer(), sender, "contract", UUID.randomUUID().toString(), new ResourceLocation("contract", name), origin);
    }
    private static JsonObject status(TimeTestModule module, WorldServer world, JsonObject started) throws Exception {
        return module.scenarioStatus(world.getMinecraftServer(), "contract", started.get("run_id").getAsString());
    }
    private static void tick(TimeTestModule module, WorldServer world) {
        module.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
    }
    private static <T> void reject(Fixture fixture, Class<T> type, String source) throws Exception {
        try { fixture.manager.decode(type, new JsonParser().parse(source)); }
        catch (JsonDeserializeException expected) { return; }
        throw new AssertionError("Unsupported definition accepted: " + source);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Fixture {
        final ContentCatalog catalog = new ContentCatalog(null);
        final DeserializerManager manager = new DeserializerManager("rml");
        final TimeTestModule module;
        final List<CompiledResource> resources = new ArrayList<>();
        Fixture(BiFunction<ResourceLocation, DataContext, Integer> functions) {
            CompiledTagModule tags = new CompiledTagModule();
            manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                    json -> new ResourceLocation(manager.decode(String.class, json))));
            NativeTypeDecoders.register(manager, tags);
            DataRules rules = new DataRules(catalog, tags, functions); rules.registerDecoders(manager);
            module = new TimeTestModule(catalog, rules, tags, new CompiledStructureModule()); module.registerDecoders(manager);
        }
        void resource(String type, String name, String source) {
            resources.add(new CompiledResource("contract", type, new ResourceLocation("contract", name), "contract:" + name, new JsonParser().parse(source)));
        }
        void publish() throws Exception {
            Constructor<ContentCatalog.Snapshot> constructor = ContentCatalog.Snapshot.class.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            Field snapshot = ContentCatalog.class.getDeclaredField("snapshot"); snapshot.setAccessible(true);
            snapshot.set(catalog, constructor.newInstance(Collections.singletonMap("contract", resources)));
        }
    }
}
