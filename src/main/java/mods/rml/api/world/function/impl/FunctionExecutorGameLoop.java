package mods.rml.api.world.function.impl;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import mods.rml.api.file.JsonHelper;
import mods.rml.api.world.function.FunctionExecutor;
import mods.rml.api.world.function.FunctionExecutorFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:56
 **/
public class FunctionExecutorGameLoop extends FunctionExecutorFactory {
    @Override
    public FunctionExecutor apply(JsonObject jsonObject) {
        ResourceLocation function = new ResourceLocation(jsonObject.get("function").getAsString());
        IntArrayList worlds = getWorlds(jsonObject);
        int ticks = -1;
        if (jsonObject.has("ticks")){
            ticks = jsonObject.get("ticks").getAsInt();
        }
        return new Executor(function, worlds, ticks);
    }
    public static class Executor extends FunctionExecutor{
        public IntArrayList worlds;
        public int ticks;

        public Executor(ResourceLocation function, IntArrayList worlds, int ticks) {
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
}
