package rml.loader.api.world.function;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Deserialized execution rule. Construction never registers listeners or runs commands. */
public abstract class FunctionExecutor {
    public enum Trigger { WORLD_LOAD, TICK }

    private final ResourceLocation function;
    private final int[] worlds;
    private final Trigger trigger;
    private final int ticks;

    protected FunctionExecutor(ResourceLocation function, IntArrayList worlds, Trigger trigger, int ticks) {
        this.function = Objects.requireNonNull(function, "function");
        this.worlds = Objects.requireNonNull(worlds, "worlds").toIntArray();
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.ticks = ticks;
    }

    public ResourceLocation getFunction() { return function; }

    public boolean matches(Trigger trigger, int dimension, long worldTime) {
        if (this.trigger != trigger) return false;
        if (trigger == Trigger.TICK && (ticks == 0 || (ticks > 0 && worldTime % ticks != 0))) return false;
        for (int world : worlds) if (world == dimension) return true;
        return false;
    }
}
