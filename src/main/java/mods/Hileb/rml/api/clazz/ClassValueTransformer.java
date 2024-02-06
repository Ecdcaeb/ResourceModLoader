package mods.Hileb.rml.api.clazz;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/6 12:46
 **/
public class ClassValueTransformer {
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
            }
        }
    }
}
