package rml.loader.content.time;

import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.SaveHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Real native disk persistence, including MapStorage's reflective reload. */
public final class TimeWorldDataContractTest {
    private TimeWorldDataContractTest() { }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Path root = Files.createTempDirectory("rml-clock-save-");
        try {
            SaveHandler saves = new SaveHandler(root.toFile(), "world", false, DataFixesManager.createFixer());
            MapStorage original = new MapStorage(saves);
            TimeWorldData state = new TimeWorldData("rml_time_state");
            ResourceLocation clockId = new ResourceLocation("demo", "clock");
            TimeWorldData.Clock clock = new TimeWorldData.Clock();
            clock.time = 123456;
            clock.rate = 0.5D;
            clock.fraction = 0.75D;
            clock.epoch = 120000;
            clock.paused = true;
            clock.configured = true;
            state.clocks.put(clockId, clock);
            TimeWorldData.Timeline timeline = new TimeWorldData.Timeline();
            timeline.started = true;
            timeline.previous = 17;
            timeline.cycle = 4;
            timeline.eventIndex = 2;
            timeline.offsets = new long[]{0, 24000, 48005};
            timeline.duration = 72000;
            state.timelines.put(new ResourceLocation("demo", "timeline"), timeline);
            original.setData("rml_time_state", state);
            state.markDirty();
            rml.loader.runtime.function.FunctionStorage functionStorage = new rml.loader.runtime.function.FunctionStorage("rml_function_storage");
            ResourceLocation functionId = new ResourceLocation("demo", "function");
            functionStorage.set(functionId, "checks.answer", new net.minecraft.nbt.NBTTagInt(42));
            original.setData("rml_function_storage", functionStorage);
            original.saveAllData();

            MapStorage reopened = new MapStorage(saves);
            TimeWorldData restored = (TimeWorldData) reopened.getOrLoadData(TimeWorldData.class, "rml_time_state");
            check(restored != null, "native world storage did not restore time state");
            TimeWorldData.Clock restoredClock = restored.clocks.get(clockId);
            check(restoredClock != null && restoredClock.time == 123456 && restoredClock.rate == 0.5D && restoredClock.fraction == 0.75D && restoredClock.epoch == 120000 && restoredClock.paused && restoredClock.configured, "clock settings were lost across native save/load");
            restoredClock.advance();
            check(restoredClock.time == 123456 && restoredClock.fraction == 0.75D, "paused clocks must retain fractional progress");
            restoredClock.paused = false;
            restoredClock.advance();
            check(restoredClock.time == 123457 && restoredClock.fraction == 0.25D, "resumed clocks must advance across the persisted fractional boundary");
            TimeWorldData.Timeline restoredTimeline = restored.timelines.get(new ResourceLocation("demo", "timeline"));
            check(restoredTimeline != null && restoredTimeline.started && restoredTimeline.previous == 17 && restoredTimeline.cycle == 4 && restoredTimeline.eventIndex == 2, "timeline progress was lost across native save/load");
            check(java.util.Arrays.equals(restoredTimeline.offsets, new long[]{0, 24000, 48005}) && restoredTimeline.duration == 72000, "sampled timeline boundaries must persist without resampling");
            rml.loader.runtime.function.FunctionStorage restoredFunctions = (rml.loader.runtime.function.FunctionStorage) reopened.getOrLoadData(rml.loader.runtime.function.FunctionStorage.class, "rml_function_storage");
            check(restoredFunctions != null && ((net.minecraft.nbt.NBTTagInt) restoredFunctions.get(functionId, "checks.answer")).getInt() == 42, "namespaced function storage was lost across native save/load");
            saves.flush();
            System.out.println("TimeWorldDataContractTest passed");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                Path[] entries = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path entry : entries) Files.delete(entry);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
