package rml.loader.deserialize;

import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerBuilder;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.utils.file.JsonHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 9:57
 **/
@PublicAPI
@EarlyClass
@BeDiscovered(BeDiscovered.PRE_INIT)
public class Deserializer {
    public static final DeserializerManager MANAGER = new DeserializerManager("rml", Deserializer::initializeManager);

    private static void initializeManager(DeserializerManager manager) {
        manager.initializer = Deserializer::initializeDeserializer;
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class, element -> new ResourceLocation(Deserializer.decode(String.class, element))));
    }

    private static void initializeDeserializer(Class<?> cls) {
        try {
            Enumeration<URL> iterator = Launch.classLoader.getResources("META-INF/rml/deserializer/" + cls.getName() + ".json");
            while (iterator.hasMoreElements()) {
                try (InputStream inputStream = iterator.nextElement().openStream()) {
                    for (String str : JsonHelper.getStringArray(JsonHelper.parse(new InputStreamReader(inputStream)))){
                        Class.forName(str, true, Launch.classLoader);
                    }
                }
            }
        } catch (Throwable throwable) {
            throw new RuntimeException("Cant handle initialize deserializer", throwable);
        }
    }

    public static <T> DeserializerBuilder<T> named(Class<T> clazz, ResourceLocation name){
        return MANAGER.named(clazz, name);
    }

    public static <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializeException {
        return MANAGER.decode(clazz, jsonElement);
    }
}
