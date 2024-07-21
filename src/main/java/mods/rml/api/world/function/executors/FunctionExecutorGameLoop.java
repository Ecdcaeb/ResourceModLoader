package mods.rml.api.world.function.executors;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import mods.rml.api.announces.BeDiscovered;
import mods.rml.api.world.function.FunctionExecutor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:56
 **/

@BeDiscovered
public class FunctionExecutorGameLoop extends FunctionExecutor {

    public static final AbstractDeserializer<FunctionExecutor> DESERIALIZER = Deserializer.named(FunctionExecutor.class, new ResourceLocation("rml", "tick"))
            .require(ResourceLocation.class, "function")
            .require(IntArrayList.class, "world")
            .require(Integer.class, "ticks")
            .decode(context -> new FunctionExecutorGameLoop(context.get(ResourceLocation.class, "function"), context.get(IntArrayList.class, "world"), context.get(Integer.class, "ticks")))
            .build();
    public IntArrayList worlds;
    public int ticks;

    public FunctionExecutorGameLoop(ResourceLocation function, IntArrayList worlds, int ticks) {
        super(function);
        this.worlds = worlds;
        this.ticks = ticks;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoop(TickEvent.WorldTickEvent event){
        if (event.phase == TickEvent.Phase.START){
            if (event.world != null){
                if (!event.world.isRemote){
                    if (worlds.contains(event.world.provider.getDimension())){
                        if (ticks != 0 && (ticks < 0 || event.world.getTotalWorldTime()%ticks == 0))
                            execute((WorldServer) event.world, this.function);
                    }
                }
            }
        }
    }
}
