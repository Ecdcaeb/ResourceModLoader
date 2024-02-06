package mods.Hileb.rml.api.clazz;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PublicAPI;
import net.minecraftforge.common.config.TypeAdapters;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/6 12:46
 **/
@EarlyClass
@PublicAPI
public class ClassValueTransformer {
    @PublicAPI
    public static void transform(Class<?> clazz, JsonObject json, Object instance) throws IllegalAccessException {
        for(Map.Entry<String, JsonElement> entry:json.entrySet()){//walk
            Field field= ReflectionHelper.findField(clazz,entry.getKey());
            JsonElement jsonElement=entry.getValue();
            if (jsonElement.isJsonObject()){
                transform(field.getType(),jsonElement.getAsJsonObject(),field.get(instance));
            }else {
                Class<?> type = field.getType();
                if (int.class == type) field.set(instance,jsonElement.getAsInt());
                else if (float.class == type) field.set(instance,jsonElement.getAsFloat());
                else if (boolean.class == type) field.set(instance,jsonElement.getAsBoolean());
                else if (byte.class == type) field.set(instance,jsonElement.getAsByte());
                else if (char.class == type) field.set(instance,jsonElement.getAsCharacter());
                else if (double.class == type) field.set(instance,jsonElement.getAsDouble());
                else if (long.class == type) field.set(instance,jsonElement.getAsLong());
                else if (String.class == type) field.set(instance,jsonElement.getAsString());
                else if (int[].class ==type) field.set(instance, G);
            }
        }
    }
    private static final HashMap<Class<?>,JsonReader<?>> cachedBaseType=new HashMap<>();
    static {
        cachedBaseType.put(boolean.class);
        cachedBaseType.put(boolean[].class);
        cachedBaseType.put(Boolean.class);
        cachedBaseType.put(Boolean[].class);
        cachedBaseType.put(float.class;
        cachedBaseType.put(float[].class);
        cachedBaseType.put(Float.class);
        cachedBaseType.put(Float[].class);
        cachedBaseType.put(double.class);
        cachedBaseType.put(double[].class);
        cachedBaseType.put(Double.class);
        cachedBaseType.put(Double[].class);
        cachedBaseType.put(byte.class);
        cachedBaseType.put(byte[].class);
        cachedBaseType.put(Byte.class);
        cachedBaseType.put(Byte[].class);
        cachedBaseType.put(char.class);
        cachedBaseType.put(char[].class);
        cachedBaseType.put(Character.class);
        cachedBaseType.put(Character[].class);
        cachedBaseType.put(short.class);
        cachedBaseType.put(short[].class);
        cachedBaseType.put(Short.class);
        cachedBaseType.put(Short[].class);
        cachedBaseType.put(int.class);
        cachedBaseType.put(int[].class);
        cachedBaseType.put(Integer.class);
        cachedBaseType.put(Integer[].class);
        cachedBaseType.put(String.class);
        cachedBaseType.put(String[].class);
    }
    public static <T> JsonReader<T> getReader(Class<T> clazz){

    }
    public interface JsonReader<T>{
        T read(JsonElement jsonElement);
    }
}
