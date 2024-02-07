package mods.Hileb.rml.api.clazz;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.file.JsonHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashMap;
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
                JsonHelper.JsonReader jsonReader = cachedBaseType.get(type);
                if (jsonReader !=null){
                    field.set(instance,jsonReader.read(jsonElement));
                }else {
                    RMLFMLLoadingPlugin.Container.LOGGER.error(new UnsupportedOperationException("the json be not supported. Error at "+ jsonElement));
                }
            }
        }
    }
    @PublicAPI public static final HashMap<Class<?>, JsonHelper.JsonReader<?>> cachedBaseType=new HashMap<>();
    static {
        register(boolean.class, JsonHelper::getBoolean);
        register(boolean[].class, JsonHelper::getBooleanArray);
        register(Boolean.class, JsonHelper::getBoolean);
        register(Boolean[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getBoolean));
        register(float.class, JsonHelper::getFloat);
        register(float[].class, JsonHelper::getFloatArray);
        register(Float.class, JsonHelper::getFloat);
        register(Float[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getFloat));
        register(double.class, JsonHelper::getDouble);
        register(double[].class, JsonHelper::getDoubleArray);
        register(Double.class, JsonHelper::getDouble);
        register(Double[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getDouble));
        register(byte.class, JsonHelper::getByte);
        register(byte[].class, JsonHelper::getByteArray);
        register(Byte.class, JsonHelper::getByte);
        register(Byte[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getByte));
        register(char.class, JsonHelper::getChar);
        register(char[].class, JsonHelper::getCharArray);
        register(Character.class, JsonHelper::getChar);
        register(Character[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getChar));
        register(short.class, JsonHelper::getShort);
        register(short[].class, JsonHelper::getShortArray);
        register(Short.class, JsonHelper::getShort);
        register(Short[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getShort));
        register(int.class, JsonHelper::getInteger);
        register(int[].class, JsonHelper::getIntegerArray);
        register(Integer.class, JsonHelper::getInteger);
        register(Integer[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getInteger));
        register(String.class, JsonHelper::getString);
        register(String[].class, (element)-> JsonHelper.getAsArray(element,JsonHelper::getString));
    }
    @PublicAPI public static <T> void register(Class<T> clazz, JsonHelper.JsonReader<T> reader){
        cachedBaseType.put(clazz,reader);
    }
}
