package mods.rml.api.world.function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import mods.rml.api.file.JsonHelper;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.Arrays;
import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:30
 **/
public abstract class FunctionExecutorFactory extends IForgeRegistryEntry.Impl<FunctionExecutorFactory> implements Function<JsonObject, FunctionExecutor> {

    public static IntArrayList getWorlds(JsonObject jsonObject){
        if (jsonObject.has("world")){
            if (jsonObject.get("world").isJsonArray()){
                return new IntArrayList(JsonHelper.getIntegerArray(jsonObject.get("world")));
            }else return new IntArrayList(new int[]{jsonObject.get("world").getAsInt()});
        }else return new IntArrayList(Arrays.asList(DimensionManager.getIDs()));
    }
}

