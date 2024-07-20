package rml.deserializer;

import com.google.common.primitives.Primitives;
import com.google.gson.JsonElement;
import net.minecraft.util.ResourceLocation;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 9:57
 **/
public class AbstractDeserializer<T>{
    private ResourceLocation resourceLocation;
    private Class<T> target;
    private IDeserializer<T> function;
    public AbstractDeserializer(ResourceLocation registerName, Class<T> target, IDeserializer<T> function){
        this.resourceLocation = registerName;
        this.target = DeserializerBuilder.avoidPrimitive(target);
        this.function = function;
    }
    public Class<T> getResultTarget(){
        return this.target;
    }
    public T deserialize(JsonElement json) throws JsonDeserializerException{
        return this.function.apply(json);
    }

    public ResourceLocation getRegisterName(){
        return this.resourceLocation;
    }

    @FunctionalInterface
    public interface IDeserializer<V>{
        V apply(JsonElement jsonElement) throws JsonDeserializerException;
    }
}
