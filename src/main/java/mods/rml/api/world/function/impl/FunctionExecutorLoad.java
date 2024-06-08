package mods.rml.api.world.function.impl;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import mods.rml.api.file.JsonHelper;
import mods.rml.api.world.function.FunctionExecutor;
import mods.rml.api.world.function.FunctionExecutorFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:56
 **/
public class FunctionExecutorLoad extends FunctionExecutorFactory {
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
        public void onWorldLoop(WorldEvent.Load event){
            if (event.getWorld() != null){
                if (!event.getWorld().isRemote){
                    if (worlds.contains(event.getWorld().provider.getDimension())){
                        execute((WorldServer) event.getWorld(), this.function);
                    }
                }
            }
        }
    }
}
