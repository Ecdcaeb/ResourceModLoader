package rml.deserializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:03
 **/
public class DeserializerManager {
    public final HashMap<Class<?>, AbstractDeserializer<?>> defaults = new HashMap<>();
    public final HashMap<Class<?>, HashMap<ResourceLocation, AbstractDeserializer<?>>> registry = new HashMap<>();

    public <T> AbstractDeserializer<T> addEntry(AbstractDeserializer<T> abstractDeserializer){
        if (!registry.containsKey(abstractDeserializer.getResultTarget())){
            registry.put(abstractDeserializer.getResultTarget(), new HashMap<>());
        }
        registry.get(abstractDeserializer.getResultTarget()).put(abstractDeserializer.getRegisterName(), abstractDeserializer);
        return abstractDeserializer;
    }

    public <T> AbstractDeserializer<T> markDefault(AbstractDeserializer<T> abstractDeserializer){
        defaults.put(abstractDeserializer.getResultTarget(), abstractDeserializer);
        return abstractDeserializer;
    }

    public <T> AbstractDeserializer<T>  addDefaultEntry(AbstractDeserializer<T> abstractDeserializer){
        addEntry(abstractDeserializer);
        markDefault(abstractDeserializer);
        return abstractDeserializer;
    }

    public <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializerException{
        try {
            if (clazz.isArray()){
                @SuppressWarnings("rawtypes")
                Class clazzComponentType = clazz.getComponentType();
                if (jsonElement.isJsonArray()){
                    JsonArray array = jsonElement.getAsJsonArray();
                    @SuppressWarnings("rawtypes")
                    ArrayList array_to_return = new ArrayList(array.size());
                    for(JsonElement element : array){
                        array_to_return.add(decode(clazzComponentType, element));
                    }
                    return clazz.cast(array_to_return.toArray());
                }else {
                    //TODO : Cannot cast [Ljava.lang.Object; to [Lmods.rml.deserialize.RMLLoaders$OreDic$TagOre;
                    return clazz.cast(new Object[]{decode(clazzComponentType, jsonElement)});
                }
            } else {
                if (jsonElement.isJsonObject()){
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject.has("type")){
                        ResourceLocation decoderName = new ResourceLocation(jsonObject.get("type").getAsString());
                        if (!registry.containsKey(clazz)) throw new JsonDeserializerException(jsonObject, "Deserializer for " + clazz +" , is not registered.");
                        HashMap<ResourceLocation, AbstractDeserializer<?>> typedRegistry = registry.get(clazz);
                        if (!typedRegistry.containsKey(decoderName)) throw new JsonDeserializerException(jsonObject, "Deserializer for " + clazz +" named " + decoderName + " could not be found.");
                        return clazz.cast(typedRegistry.get(decoderName).deserialize(jsonObject));
                    }else {
                        if (defaults.containsKey(clazz)){
                            return clazz.cast(defaults.get(clazz).deserialize(jsonObject));
                        }else {
                            throw new JsonDeserializerException(jsonObject, "Required Field \"type\" is not found in such a JsonObject. No default founded for Type " + clazz + ".");
                        }
                    }
                }else {
                    if (defaults.containsKey(clazz)){
                        return clazz.cast(defaults.get(clazz).deserialize(jsonElement));
                    }else {
                        throw new JsonDeserializerException(jsonElement, "Required Field \"type\" is not found in such a JsonObject. No default founded for Type " + clazz + ".");
                    }
                }
            }
        }catch (Throwable throwable){
            if (throwable instanceof JsonDeserializerException) throw throwable;
            else throw new JsonDeserializerException(jsonElement, "unexpected Exception when decoding json, ", throwable);
        }
    }

    public DeserializerManager(){
        ResourceLocation GSON = new ResourceLocation("google", "primitive");
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, int.class, JsonElement::getAsInt));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, float.class, JsonElement::getAsFloat));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, double.class, JsonElement::getAsDouble));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, long.class, JsonElement::getAsLong));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, char.class, JsonElement::getAsCharacter));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, byte.class, JsonElement::getAsByte));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Integer.class, JsonElement::getAsInt));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Float.class, JsonElement::getAsFloat));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Double.class, JsonElement::getAsDouble));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Long.class, JsonElement::getAsLong));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Character.class, JsonElement::getAsCharacter));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Byte.class, JsonElement::getAsByte));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, String.class, JsonElement::getAsString));
    }

    public <T> DeserializerBuilder<T> named(Class<T> clazz, ResourceLocation name){
        return new DeserializerBuilder<>(this, clazz, name);
    }
}
