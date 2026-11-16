package rml.loader.deserialize.function.executor;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.world.function.FunctionExecutor;
import rml.loader.deserialize.Deserializer;

@BeDiscovered(BeDiscovered.PRE_INIT)
public final class FunctionExecutorWorldLoad extends FunctionExecutor {
    public static final AbstractDeserializer<FunctionExecutor> DESERIALIZER = Deserializer.named(FunctionExecutor.class, new ResourceLocation("rml", "load"))
            .require(ResourceLocation.class, "function")
            .require(IntArrayList.class, "world")
            .decode(context -> new FunctionExecutorWorldLoad(context.get(ResourceLocation.class, "function"),
                    context.get(IntArrayList.class, "world")))
            .build();

    public FunctionExecutorWorldLoad(ResourceLocation function, IntArrayList worlds) {
        super(function, worlds, Trigger.WORLD_LOAD, 1);
    }
}
