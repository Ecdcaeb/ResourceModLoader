package mods.rml.api.function.impl;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import mods.rml.api.file.JsonHelper;
import mods.rml.api.function.FunctionExecutor;
import mods.rml.api.function.FunctionExecutorFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:56
 **/
public class FunctionExecutorGameLoop extends FunctionExecutorFactory {
    @Override
    public FunctionExecutor apply(JsonObject jsonObject) {
        ResourceLocation function = new ResourceLocation(jsonObject.get("function").getAsString());
        IntArrayList worlds;
        if (jsonObject.get("world").isJsonArray()){
            worlds = new IntArrayList(JsonHelper.getIntegerArray(jsonObject.get("world")));
        }else worlds = new IntArrayList(new int[]{jsonObject.get("world").getAsInt()});
        return new Executor(function, worlds);
    }
    public static class Executor extends FunctionExecutor{
        public IntArrayList worlds;

        public Executor(ResourceLocation function, IntArrayList worlds) {
            super(function);
            this.worlds = worlds;
            MinecraftForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent
        public void onWorldLoop(TickEvent.WorldTickEvent event){
            if (event.phase == TickEvent.Phase.START){
                if (event.world != null){
                    if (!event.world.isRemote){
                        if (worlds.contains(event.world.provider.getDimension())){
                            execute((WorldServer) event.world, this.function);
                        }
                    }
                }
            }
        }
    }
}
