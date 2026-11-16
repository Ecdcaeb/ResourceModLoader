package rml.deserializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.utils.ClassHelper;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:03
 **/

@EarlyClass
@PublicAPI
public class DeserializerManager {
    public final String defaultDomain;
    public final HashMap<Class<?>, AbstractDeserializer<?>> defaults = new HashMap<>();
    public final HashMap<Class<?>, HashMap<ResourceLocation, AbstractDeserializer<?>>> registry = new HashMap<>();
    public Consumer<Class<?>> initializer = (str) -> {};
    public final Set<Class<?>> initializedClasses = new HashSet<>();
    private final Map<Class<?>, GenericDeserializer> genericDecoders = new HashMap<>();

    @FunctionalInterface
    public interface GenericDeserializer {
        Object deserialize(DeserializerManager manager, Type[] arguments, JsonElement value) throws JsonDeserializeException;
    }

    public void addGenericEntry(Class<?> rawType, GenericDeserializer decoder) {
        genericDecoders.put(Objects.requireNonNull(rawType, "rawType"), Objects.requireNonNull(decoder, "decoder"));
    }

    /**
     * Add a deserializer
     *
     * @param abstractDeserializer the deserializer
     * @param <T> type
     *
     * @return the deserializer you input.
     */
    public <T> AbstractDeserializer<T> addEntry(AbstractDeserializer<T> abstractDeserializer){
        if (!registry.containsKey(abstractDeserializer.getResultTarget())){
            registry.put(abstractDeserializer.getResultTarget(), new HashMap<>());
        }
        registry.get(abstractDeserializer.getResultTarget()).put(abstractDeserializer.getRegisterName(), abstractDeserializer);
        return abstractDeserializer;
    }

    /**
     * make a deserializer default
     *
     * @param abstractDeserializer the deserializer
     * @param <T> type
     *
     * @return the deserializer you input.
     */
    public <T> AbstractDeserializer<T> markDefault(AbstractDeserializer<T> abstractDeserializer){
        defaults.put(abstractDeserializer.getResultTarget(), abstractDeserializer);
        return abstractDeserializer;
    }

    /**
     * Add a deserializer and mark it as default.
     *
     * @param abstractDeserializer the deserializer
     * @param <T> type
     *
     * @return the deserializer you input.
     */
    public <T> AbstractDeserializer<T>  addDefaultEntry(AbstractDeserializer<T> abstractDeserializer){
        addEntry(abstractDeserializer);
        markDefault(abstractDeserializer);
        return abstractDeserializer;
    }

    /**
     * @param clazz the class. Should not be primitive.
     * @param jsonElement the json element.
     * @param <T> the type.
     *
     * @return the value obj.
     *
     * @throws JsonDeserializeException the exception. Error format? Unexpected context?
     */
    public <T> T decode(Class<T> clazz, JsonElement jsonElement) throws JsonDeserializeException {
        ClassHelper.forceInitAll(clazz);
        clazz = DeserializerBuilder.avoidPrimitive(clazz);

        try {
            if (jsonElement == null) throw new JsonDeserializeException(null, "Missing value for " + clazz.getTypeName());
            if (JsonElement.class.isAssignableFrom(clazz)) return clazz.cast(jsonElement);
            if (clazz.isArray()){
                Class<?> clazzComponentType = clazz.getComponentType();
                if (jsonElement.isJsonArray()){
                    JsonArray array = jsonElement.getAsJsonArray();
                    Object arrayToReturn = Array.newInstance(clazzComponentType, array.size());
                    for(int index = 0 ; index < array.size() ; index++){
                        Array.set(arrayToReturn, index, decode(clazzComponentType, array.get(index)));
                    }
                    return clazz.cast(arrayToReturn);
                }else {
                    Object arrayToReturn = Array.newInstance(clazzComponentType, 1);
                    Array.set(arrayToReturn, 0, decode(clazzComponentType, jsonElement));
                    return clazz.cast(arrayToReturn);
                }
            } else {
                if (!initializedClasses.contains(clazz)) {
                    initializer.accept(clazz);
                    initializedClasses.add(clazz);
                }
                if (clazz.isEnum() && !defaults.containsKey(clazz) && jsonElement.isJsonPrimitive()) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    T value = (T) Enum.valueOf((Class) clazz, jsonElement.getAsString().toUpperCase(Locale.ROOT));
                    return value;
                }
                if (jsonElement.isJsonObject()){
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject.has("type")){
                        ResourceLocation decoderName = parseLocation(jsonObject.get("type").getAsString());
                        HashMap<ResourceLocation, AbstractDeserializer<?>> typedRegistry = registry.get(clazz);
                        if (typedRegistry == null) {
                            throw new JsonDeserializeException(jsonElement, "DeserializerRegistry for " + clazz.getName() + " is null");
                        }
                        if (!typedRegistry.containsKey(decoderName)) throw new JsonDeserializeException(jsonObject, "Deserializer for " + clazz +" named " + decoderName + " could not be found.");
                        return clazz.cast(typedRegistry.get(decoderName).deserialize(jsonObject));
                    }else {
                        if (defaults.containsKey(clazz)){
                            return clazz.cast(defaults.get(clazz).deserialize(jsonObject));
                        }else {
                            throw new JsonDeserializeException(jsonObject, "Required Field \"type\" is not found in such a JsonObject. No default founded for Type " + clazz + ".");
                        }
                    }
                }else {
                    if (defaults.containsKey(clazz)){
                        return clazz.cast(defaults.get(clazz).deserialize(jsonElement));
                    }else {
                        throw new JsonDeserializeException(jsonElement, "Required Field \"type\" is not found in such a JsonObject. No default founded for Type " + clazz + ".");
                    }
                }
            }
        }catch (Throwable throwable){
            if (throwable instanceof JsonDeserializeException) throw throwable;
            else throw new JsonDeserializeException(jsonElement, "unexpected Exception when decoding json, ", throwable);
        }
    }

    /** Preserves nested element types; consumers can use Gson's existing TypeToken. */
    @SuppressWarnings("unchecked")
    public <T> T decode(Type type, JsonElement value) throws JsonDeserializeException {
        Objects.requireNonNull(type, "type");
        if (type instanceof Class<?>) return (T) decode((Class<?>) type, value);
        try {
            if (type instanceof WildcardType) {
                WildcardType wildcard = (WildcardType) type;
                if (wildcard.getLowerBounds().length != 0 || wildcard.getUpperBounds()[0] == Object.class) {
                    throw new JsonDeserializeException(value, "A concrete decoded value type is required: " + type);
                }
                return decode(wildcard.getUpperBounds()[0], value);
            }
            if (type instanceof GenericArrayType) {
                Type component = ((GenericArrayType) type).getGenericComponentType();
                int count = value.isJsonArray() ? value.getAsJsonArray().size() : 1;
                Object result = Array.newInstance(rawClass(component), count);
                for (int index = 0; index < count; index++) {
                    JsonElement element = value.isJsonArray() ? value.getAsJsonArray().get(index) : value;
                    Array.set(result, index, decodeElement(component, element, "[" + index + "]"));
                }
                return (T) result;
            }
            if (!(type instanceof ParameterizedType)) {
                throw new JsonDeserializeException(value, "Unsupported unresolved decoded type: " + type);
            }
            ParameterizedType parameterized = (ParameterizedType) type;
            Class<?> raw = rawClass(parameterized.getRawType());
            Type[] arguments = parameterized.getActualTypeArguments();
            GenericDeserializer custom = genericDecoders.get(raw);
            if (custom != null) return (T) custom.deserialize(this, arguments, value);
            if (raw == Optional.class) {
                return (T) (value == null || value.isJsonNull() ? Optional.empty() : Optional.ofNullable(decode(arguments[0], value)));
            }
            if (value == null || value.isJsonNull()) throw new JsonDeserializeException(value, "Missing value for " + type);
            if (raw == List.class || raw == Collection.class || raw == Iterable.class || raw == Set.class) {
                int count = value.isJsonArray() ? value.getAsJsonArray().size() : 1;
                Collection<Object> result = raw == Set.class ? new LinkedHashSet<>() : new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    JsonElement element = value.isJsonArray() ? value.getAsJsonArray().get(index) : value;
                    result.add(decodeElement(arguments[0], element, "[" + index + "]"));
                }
                return (T) result;
            }
            if (raw == Map.class) {
                if (!value.isJsonObject()) throw new JsonDeserializeException(value, "Expected an object for " + type);
                Map<Object, Object> result = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    Object key = decodeElement(arguments[0], new JsonPrimitive(entry.getKey()), "." + entry.getKey() + " (key)");
                    if (result.containsKey(key)) throw new JsonDeserializeException(value, "Duplicate decoded map key: " + entry.getKey());
                    result.put(key, decodeElement(arguments[1], entry.getValue(), "." + entry.getKey()));
                }
                return (T) result;
            }
            throw new JsonDeserializeException(value, "No generic deserializer registered for " + type);
        } catch (JsonDeserializeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new JsonDeserializeException(value, "Cannot decode " + type.getTypeName(), failure);
        }
    }

    private Object decodeElement(Type type, JsonElement value, String path) throws JsonDeserializeException {
        try {
            return decode(type, value);
        } catch (JsonDeserializeException failure) {
            throw new JsonDeserializeException(value, "At " + path + ": " + failure.getMessage(), failure);
        }
    }

    public static Class<?> rawClass(Type type) {
        if (type instanceof Class<?>) return (Class<?>) type;
        if (type instanceof ParameterizedType) return rawClass(((ParameterizedType) type).getRawType());
        if (type instanceof GenericArrayType) return Array.newInstance(rawClass(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        if (type instanceof WildcardType) return rawClass(((WildcardType) type).getUpperBounds()[0]);
        throw new IllegalArgumentException("Unresolved decoded type: " + type);
    }


    public <T> T decodeSilently(Class<T> clazz, JsonElement jsonElement){
        JsonDeserializeException exception = null;
        try {
            return decode(clazz, jsonElement);
        } catch (JsonDeserializeException e) {
            exception = e;
        }
        throw new UnsupportedOperationException(exception);
    }

    /**
     * @param defaultDomain the default domain of one deserializer manager
     *
     * Constructor, with build-in default deserializers.
     */
    public DeserializerManager(String defaultDomain){
        this(defaultDomain, (manager)->{});
    }


    /**
     * @param defaultDomain the default domain of one deserializer manager
     * @param consumer register the buildIn deserializer
     *
     * Constructor, with build-in default deserializers.
     */
    public DeserializerManager(String defaultDomain, Consumer<DeserializerManager> consumer){
        this.defaultDomain = defaultDomain;
        ResourceLocation GSON = new ResourceLocation("google", "primitive");
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Integer.class, AbstractDeserializer.safeRun(JsonElement::getAsInt)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Float.class, AbstractDeserializer.safeRun(JsonElement::getAsFloat)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Double.class, AbstractDeserializer.safeRun(JsonElement::getAsDouble)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Long.class, AbstractDeserializer.safeRun(JsonElement::getAsLong)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Character.class, AbstractDeserializer.safeRun(JsonElement::getAsCharacter)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Byte.class, AbstractDeserializer.safeRun(JsonElement::getAsByte)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, String.class, AbstractDeserializer.safeRun(JsonElement::getAsString)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Boolean.class, AbstractDeserializer.safeRun(JsonElement::getAsBoolean)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Short.class, AbstractDeserializer.safeRun(JsonElement::getAsShort)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, BigInteger.class, AbstractDeserializer.safeRun(JsonElement::getAsBigInteger)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, BigDecimal.class, AbstractDeserializer.safeRun(JsonElement::getAsBigDecimal)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Number.class, AbstractDeserializer.safeRun(JsonElement::getAsNumber)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, Void.class, AbstractDeserializer.safeRun((jsonElement)->null)));
        this.addDefaultEntry(new AbstractDeserializer<>(GSON, JsonObject.class, AbstractDeserializer.safeRun(JsonElement::getAsJsonObject)));
        consumer.accept(this);
    }

    public DeserializerManager(){
        this("minecraft");
    }

    public ResourceLocation parseLocation(String toSplit)
    {
        String[] astring = new String[] {defaultDomain, toSplit};
        int i = toSplit.indexOf(':');

        if (i >= 0)
        {
            astring[1] = toSplit.substring(i + 1);

            if (i > 0)
            {
                astring[0] = toSplit.substring(0, i);
            }
        }

        return new ResourceLocation(astring[0], astring[1]);
    }

    public <T, F> AbstractDeserializer<T> map(final Class<T> tClass, final Class<F> fClass, final ResourceLocation resourceLocation, final Function<F, T> function){
        return new AbstractDeserializer<>(resourceLocation, tClass, jsonElement -> function.apply(DeserializerManager.this.decode(fClass, jsonElement)));
    }

    public static JsonElement getFromPath(JsonObject jsonObject, String path){
        int i = path.indexOf('.');
        if (i == -1){
            return jsonObject.get(path);
        }else {
            String first = path.substring(0, i);
            JsonElement element1 = jsonObject.get(first);
            if (element1 instanceof JsonObject){
                return getFromPath((JsonObject)element1, path.substring(i + 1));
            }else return null;
        }
    }


    /**
     * @param clazz the class, you should not use primitive type, although it is supported by the auto-boxing by rml.
     * @param name the register name of a named Deserializer
     * @param <T> the type of decoded. Yes, it could not be primitive.
     *
     * @return the builder.
     */
    public <T> DeserializerBuilder<T> named(Class<T> clazz, ResourceLocation name){
        return new DeserializerBuilder<>(this, clazz, name);
    }
}
