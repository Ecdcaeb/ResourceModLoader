package rml.deserializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import mods.rml.api.announces.BeDiscovered;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 9:57
 **/

@BeDiscovered
public class Deserializer {
    public static final DeserializerManager MANAGER = new DeserializerManager();

    public static <T> DeserializerBuilder<T> named(Class<T> clazz, ResourceLocation name){
        return MANAGER.named(clazz, name);
    }

    public static <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializerException{
        return MANAGER.decode(clazz, jsonElement);
    }
}
