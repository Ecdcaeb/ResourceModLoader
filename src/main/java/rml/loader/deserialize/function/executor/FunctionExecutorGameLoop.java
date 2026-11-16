package rml.loader.deserialize.function.executor;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.world.function.FunctionExecutor;
import rml.loader.deserialize.Deserializer;

@BeDiscovered(BeDiscovered.PRE_INIT)
public final class FunctionExecutorGameLoop extends FunctionExecutor {
    public static final AbstractDeserializer<FunctionExecutor> DESERIALIZER = Deserializer.named(FunctionExecutor.class, new ResourceLocation("rml", "tick"))
            .require(ResourceLocation.class, "function")
            .require(IntArrayList.class, "world")
            .optionalDefault(Integer.class, "ticks", 1)
            .decode(context -> new FunctionExecutorGameLoop(context.get(ResourceLocation.class, "function"),
                    context.get(IntArrayList.class, "world"), context.get(Integer.class, "ticks")))
            .build();

    public FunctionExecutorGameLoop(ResourceLocation function, IntArrayList worlds, int ticks) {
        super(function, worlds, Trigger.TICK, ticks);
    }
}
