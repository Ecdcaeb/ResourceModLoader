package rml.deserializer;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:55
 **/
public class DeserializerBuilder<T> {
    private DeserializerManager manager;
    private Class<T> clazz;
    private ResourceLocation resourceLocation;
    private HashSet<Action> actions = new HashSet<>();
    private Function<Context, T> function = (context)->null;
    private boolean isDefault = false;
    public DeserializerBuilder(DeserializerManager manager, Class<T> clazz, ResourceLocation resourceLocation){
        this.manager = manager;
        this.clazz = clazz;
        this.resourceLocation = resourceLocation;
    }

    public DeserializerBuilder<T> require(Class<?> clazz, String name){
        this.actions.add(new Action(name, clazz, (c)->false));
        return this;
    }

    public DeserializerBuilder<T> optional(Class<?> clazz, String name){
        return optionalWhen(clazz, name, c->true);
    }

    public <V> DeserializerBuilder<T> optionalDefault(Class<V> clazz, String name, V defaultValue){
        return optionalDefaultWhen(clazz, name, c->true, defaultValue);
    }

    public DeserializerBuilder<T> optionalWhen(Class<?> clazz, String name, Context.ToBooleanFunction isNotRequired){
        this.actions.add(new Action(name, clazz, isNotRequired));
        return this;
    }

    public <V> DeserializerBuilder<T> optionalDefaultWhen(Class<V> clazz, String name,Context.ToBooleanFunction isNotRequired, V defaultValue){
        this.actions.add(new ActionDefault(name, clazz, isNotRequired, defaultValue));
        return this;
    }

    public DeserializerBuilder<T> check(Function<Context, JsonDeserializerException> function){
        this.actions.add(new CheckAction(function));
        return this;
    }

    public DeserializerBuilder<T> decode(Function<Context, T> function){
        this.function = function;
        return this;
    }

    public DeserializerBuilder<T> markDefault(){
        this.isDefault = true;
        return this;
    }

    public AbstractDeserializer<T> build(){
        DeserializerManager manager1 = manager;
        AbstractDeserializer<T> deserializer = new AbstractDeserializer<>(resourceLocation, clazz,
                (jsonElement)->{
                    if (!jsonElement.isJsonObject()) throw new JsonDeserializerException(jsonElement, "Only Support JsonObject.");
                    else {
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        Context context = new Context(jsonObject);
                        for(Action action : actions){
                            action.execute(manager1, jsonObject, context);
                        }
                        return function.apply(context);
                    }
                }
        );
        if (this.isDefault) manager1.addDefaultEntry(deserializer);
        else manager1.addEntry(deserializer);
        return deserializer;
    }

    public static class Context{
        public JsonObject jsonObject;
        public HashMap<String, Object> environments;
        public Context(JsonObject jsonObject){
            this.jsonObject = jsonObject;
            this.environments = new HashMap<>();
        }

        public JsonObject getJsonObject(){
            return jsonObject;
        }

        public HashMap<String, Object> getEnvironments() {
            return environments;
        }

        public boolean ifPresent(String s){
            return environments.containsKey(s);
        }

        public boolean ifPresent(Class<?> clazz,String s){
            return environments.containsKey(s) && clazz.isInstance(environments.get(s));
        }

        public Object get(String s){
            return environments.get(s);
        }

        public <T> T get(Class<T> clazz, String s){
            return clazz.cast(environments.get(s));
        }

        public void put(String s, Object obj){
            environments.put(s, obj);
        }

        @FunctionalInterface
        public interface ToBooleanFunction{
            boolean apply(Context context);
        }

    }

    private static class Action{
        public String name;
        public Class<?> type;
        public Context.ToBooleanFunction isNotRequired;
        public Action(String name, Class<?> type, Context.ToBooleanFunction isNotRequired){
            this.name = name;
            this.type = type;
            this.isNotRequired = isNotRequired;
        }

        public void execute(DeserializerManager manager1, JsonObject jsonObject, Context context) throws JsonDeserializerException{
            if (jsonObject.has(this.name)){
                try{
                    Object obj = manager1.decode(this.type, jsonObject.get(this.name));
                    context.put(this.name, obj);
                }catch (JsonDeserializerException e){
                    if (!this.isNotRequired.apply(context)){
                        throw new JsonDeserializerException(jsonObject, "field " + this.name + " decoding error!", e);
                    }
                }
            }else if (!this.isNotRequired.apply(context)){
                throw new JsonDeserializerException(jsonObject, "field " + this.name +" is required");
            }
        }
    }

    private static class ActionDefault extends Action{
        private Object defaultValue;

        public ActionDefault(String name, Class<?> type, Context.ToBooleanFunction isNotRequired, Object defaultValue) {
            super(name, type, isNotRequired);
            this.defaultValue = defaultValue;
        }

        @Override
        public void execute(DeserializerManager manager1, JsonObject jsonObject, Context context) throws JsonDeserializerException {
            if (jsonObject.has(this.name)){
                try{
                    Object obj = manager1.decode(this.type, jsonObject.get(this.name));
                    context.put(this.name, obj);
                }catch (JsonDeserializerException e){
                    if (!this.isNotRequired.apply(context)){
                        throw new JsonDeserializerException(jsonObject, "field " + this.name + " decoding error!", e);
                    }
                }
            }else if (!this.isNotRequired.apply(context)){
                context.put(this.name, defaultValue);
            }
        }
    }


    private static class CheckAction extends Action{
        private Function<Context, JsonDeserializerException> check;

        public CheckAction(Function<Context, JsonDeserializerException> check) {
            super(null, null, null);
            this.check = check;
        }

        @Override
        public void execute(DeserializerManager manager1, JsonObject jsonObject, Context context) throws JsonDeserializerException {
            JsonDeserializerException exception = this.check.apply(context);
            if (exception != null) throw exception;
        }
    }


}
