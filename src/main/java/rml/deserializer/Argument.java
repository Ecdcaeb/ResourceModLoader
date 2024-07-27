package rml.deserializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/27 12:49
 **/
public abstract class Argument<T> implements DeserializerBuilder.IAction {
    public final String name;

    public Argument(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public T cast(DeserializerBuilder.Context context) {
        return (T) context.get(this.name);
    }

    public static <E> Argument<E> type(String name, Class<E> clazz) {
        return new Argument<E>(name) {
            @Override
            public void execute(DeserializerManager manager, JsonObject jsonObject, DeserializerBuilder.Context context) throws JsonDeserializeException {
                if (jsonObject.has(name)) {
                    try {
                        final Class<?> clazz2 = DeserializerBuilder.avoidPrimitive(clazz);
                        Object obj = manager.decode(clazz2, jsonObject.get(name));
                        context.put(name, obj);
                    } catch (JsonDeserializeException e) {
                        throw new JsonDeserializeException(jsonObject, "field " + name + " decoding error!", e);
                    }
                } else {
                    throw new JsonDeserializeException(jsonObject, "field " + name + " is required");
                }
            }
        };
    }

    public static Argument<Integer> integer(String name, Function<Integer, Throwable> predicate) {
        return new Argument<Integer>(name) {
            @Override
            public void execute(DeserializerManager manager, JsonObject jsonObject, DeserializerBuilder.Context context) throws JsonDeserializeException {
                if (jsonObject.has(name)) {
                    Integer obj = null;
                    try {
                        obj = manager.decode(Integer.class, jsonObject.get(name));
                    } catch (JsonDeserializeException e) {
                        throw new JsonDeserializeException(jsonObject, "field " + name + " decoding error!", e);
                    } finally {
                        if (obj != null) {
                            Throwable throwable = predicate.apply(obj);
                            if (throwable != null) throw new JsonDeserializeException(jsonObject, throwable);
                            context.put(name, obj);
                        }
                    }
                } else {
                    throw new JsonDeserializeException(jsonObject, "field " + name + " is required");
                }
            }
        };
    }

    public static Argument<Integer> integer(String name) {
        return integer(name, (i) -> null);
    }

    public static Argument<Integer> integer(String name, int min, int max) {
        return integer(name, (i) -> (i > max || i < min) ? new IllegalArgumentException("integer must in range [" + min + "," + max + "], current value : " + i) : null);
    }

    public static Argument<Integer> positiveInteger(String name) {
        return integer(name, 1, Integer.MAX_VALUE);
    }

    public static Argument<Float> floatArgument(String name, Function<Float, Throwable> predicate){
        return new Argument<Float>(name) {
            @Override
            public void execute(DeserializerManager manager, JsonObject jsonObject, DeserializerBuilder.Context context) throws JsonDeserializeException {
                if (jsonObject.has(name)){
                    Float obj = null;
                    try{
                        obj = manager.decode(Float.class, jsonObject.get(name));
                    }catch (JsonDeserializeException e){
                        throw new JsonDeserializeException(jsonObject, "field " + name + " decoding error!", e);
                    }finally {
                        if (obj != null){
                            Throwable throwable = predicate.apply(obj);
                            if (throwable != null) throw new JsonDeserializeException(jsonObject, throwable);
                            context.put(name, obj);
                        }
                    }
                }else {
                    throw new JsonDeserializeException(jsonObject, "field " + name +" is required");
                }
            }
        };
    }

    public static Argument<Float> floatArgument(String name){
        return floatArgument(name, (i)->null);
    }

    public static Argument<Float> floatArgument(String name, float min, float max){
        return floatArgument(name, (i)->(i > max || i < min) ? new IllegalArgumentException("float must in range ["+min+","+max+"], current value : "+i) : null);
    }

    public static Argument<Float> positiveFloat(String name){
        return floatArgument(name, 0, Float.MAX_VALUE);
    }

    public static Argument<Boolean> bool(String name, Function<Boolean, Throwable> predicate){
        return new Argument<Boolean>(name) {
            @Override
            public void execute(DeserializerManager manager, JsonObject jsonObject, DeserializerBuilder.Context context) throws JsonDeserializeException {
                if (jsonObject.has(name)){
                    Boolean obj = null;
                    try{
                        obj = manager.decode(Boolean.class, jsonObject.get(name));
                    }catch (JsonDeserializeException e){
                        throw new JsonDeserializeException(jsonObject, "field " + name + " decoding error!", e);
                    }finally {
                        if (obj != null){
                            Throwable throwable = predicate.apply(obj);
                            if (throwable != null) throw new JsonDeserializeException(jsonObject, throwable);
                            context.put(name, obj);
                        }
                    }
                }else {
                    throw new JsonDeserializeException(jsonObject, "field " + name +" is required");
                }
            }
        };
    }

    public static<T> Argument<Map<String, T>> map(String name, final Class<T> clazz){
        return new Argument<Map<String, T>>(name) {
            @Override
            public void execute(DeserializerManager manager, JsonObject jsonObject, DeserializerBuilder.Context context) throws JsonDeserializeException {
                if (jsonObject.has(name)){
                    JsonElement jsonElement = jsonObject.get(name);
                    if (!jsonElement.isJsonObject()) throw new JsonDeserializeException(jsonObject, "field" + name + "is required be json object.");
                    try {
                        Map<String, T> map = jsonElement.getAsJsonObject().entrySet().stream().collect(Collectors.toMap((entry) -> entry.getKey(), (entry) -> manager.decodeSilently(clazz, entry.getValue())));
                        context.put(name, map);
                    }catch (Exception e){
                        throw new JsonDeserializeException(jsonElement, e);
                    }
                }else {
                    throw new JsonDeserializeException(jsonObject, "field " + name +" is required");
                }
            }
        };
    }


}
