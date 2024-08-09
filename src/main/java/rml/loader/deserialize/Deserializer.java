package rml.loader.deserialize;

import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.registries.IForgeRegistry;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerBuilder;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.jrx.announces.BeDiscovered;
import net.minecraft.util.ResourceLocation;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 9:57
 **/

@BeDiscovered
public class Deserializer {
    public static final DeserializerManager MANAGER = new DeserializerManager("rml",
            manager -> {
                manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class, element -> new ResourceLocation(Deserializer.decode(String.class, element))));

    });

    public static <T> DeserializerBuilder<T> named(Class<T> clazz, ResourceLocation name){
        return MANAGER.named(clazz, name);
    }

    public static <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializeException {
        return MANAGER.decode(clazz, jsonElement);
    }
}
