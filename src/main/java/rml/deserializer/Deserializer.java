package rml.deserializer;

import com.google.gson.JsonElement;
import rml.jrx.announces.BeDiscovered;
import net.minecraft.util.ResourceLocation;

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

    public static <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializeException {
        return MANAGER.decode(clazz, jsonElement);
    }
}
