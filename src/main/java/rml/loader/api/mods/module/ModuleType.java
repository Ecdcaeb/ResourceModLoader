package rml.loader.api.mods.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.core.RMLFMLLoadingPlugin;
import rml.loader.deserialize.Deserializer;

import java.util.Arrays;
import java.util.HashMap;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:54
 **/

@PublicAPI
public class ModuleType{
    public static final AbstractDeserializer<ModuleType> DESERIALIZE_CONSTRUCTOR = Deserializer.named(ModuleType.class, new ResourceLocation("rml", "new_type"))
            .require(ResourceLocation.class, "name")
            .require(Boolean.class, "isFile")
            .require(String.class, "defaultLocation")
            .decode((context)->{
                ResourceLocation location = context.get(ResourceLocation.class, "name");
                return valueOf(location) == null ?
                        new ModuleType(context.get(ResourceLocation.class, "name"), context.get(String.class, "defaultLocation"), context.get(Boolean.class, "isFile")) :
                        valueOf(location);
            }).markDefault().build();

    public static ModuleType decodeAndRegister(JsonElement jsonElement) throws JsonDeserializeException {
        try {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            ResourceLocation resourceLocation = new ResourceLocation(jsonObject.get("name").getAsString());
            boolean isFile = jsonObject.get("isFile").getAsBoolean();
            String location = jsonObject.get("defaultLocation").getAsString();
            return register(resourceLocation, location, isFile);
        } catch (Throwable throwable) {
            throw new JsonDeserializeException(jsonElement, "cant decode ModuleType");
        }
    }

    public static final HashMap<ResourceLocation, ModuleType> REGISTRY = new HashMap<>();
//    public static final ModuleType STRUCTURE = register("STRUCTURE","structures", false);//TODO
//    public static final ModuleType DIMENSION = register("DIMENSION", "dimension", false);//TODO

    private static ModuleType register(ResourceLocation location, String defaultLocation, boolean isFile){
        return new ModuleType(location, defaultLocation, isFile);
    }

    public ResourceLocation name;
    public final boolean isFile;
    public final String defaultLocation;

    public ModuleType(ResourceLocation name, String defaultLocationIn, boolean isFileIn) {
        this.name = name;
        this.defaultLocation = defaultLocationIn;
        this.isFile = isFileIn;
        RMLFMLLoadingPlugin.LOGGER.info("Module Type Registered: {}", this);
        REGISTRY.put(name, this);
    }

    public ResourceLocation getName() {
        return name;
    }

    public String getDefaultLocation() {
        return defaultLocation;
    }

    public boolean isFile() {
        return isFile;
    }

    public static Module[] getAllForDefault() {
        return Arrays.stream(ModuleType.values()).map(Module::new).toArray(Module[]::new);
    }

    public static ModuleType[] values(){
        return REGISTRY.values().toArray(new ModuleType[0]);
    }

    @PrivateAPI
    public static ModuleType valueOf(String s){
        if (s.indexOf(':') >= 0) return valueOf(new ResourceLocation(s));
        else return valueOf(new ResourceLocation("rml", s));
    }

    public static ModuleType valueOf(ResourceLocation resourceLocation){
        return REGISTRY.get(resourceLocation);
    }

    @Override
    public String toString() {
        return "ModuleType{" +
                "name=" + name +
                ", isFile=" + isFile +
                ", defaultLocation='" + defaultLocation + '\'' +
                '}';
    }
}
