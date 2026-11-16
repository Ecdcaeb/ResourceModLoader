package rml.loader.api.mods.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.deserialize.Deserializer;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable startup module type. Conflicting declarations never silently overwrite a provider. */
@PublicAPI
public final class ModuleType {
    private static final Map<ResourceLocation, ModuleType> REGISTRY = new TreeMap<>();

    public static final AbstractDeserializer<ModuleType> DESERIALIZE_CONSTRUCTOR = Deserializer.named(ModuleType.class, new ResourceLocation("rml", "new_type"))
            .require(ResourceLocation.class, "name")
            .require(Boolean.class, "isFile")
            .require(String.class, "defaultLocation")
            .decode(context -> register(context.get(ResourceLocation.class, "name"),
                    context.get(String.class, "defaultLocation"), context.get(Boolean.class, "isFile")))
            .markDefault().build();

    private final ResourceLocation name;
    private final boolean isFile;
    private final String defaultLocation;

    private ModuleType(ResourceLocation name, String defaultLocation, boolean isFile) {
        this.name = name;
        this.defaultLocation = defaultLocation;
        this.isFile = isFile;
    }

    public static ModuleType register(ResourceLocation name, String defaultLocation, boolean isFile) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultLocation, "defaultLocation");
        ModuleType existing = REGISTRY.get(name);
        if (existing != null) {
            if (existing.isFile != isFile || !existing.defaultLocation.equals(defaultLocation)) {
                throw new IllegalArgumentException("Conflicting RML module type: " + name);
            }
            return existing;
        }
        ModuleType type = new ModuleType(name, defaultLocation, isFile);
        REGISTRY.put(name, type);
        return type;
    }

    public static ModuleType decodeAndRegister(JsonElement element) throws JsonDeserializeException {
        try {
            JsonObject object = element.getAsJsonObject();
            return register(new ResourceLocation(object.get("name").getAsString()),
                    object.get("defaultLocation").getAsString(), object.get("isFile").getAsBoolean());
        } catch (RuntimeException e) {
            throw new JsonDeserializeException(element, e);
        }
    }

    public ResourceLocation getName() { return name; }
    public String getDefaultLocation() { return defaultLocation; }
    public boolean isFile() { return isFile; }

    public static Module[] getAllForDefault() {
        return Arrays.stream(values()).map(Module::new).toArray(Module[]::new);
    }

    public static ModuleType[] values() {
        return REGISTRY.values().toArray(new ModuleType[0]);
    }

    public static ModuleType valueOf(String name) {
        return valueOf(name.indexOf(':') >= 0 ? new ResourceLocation(name) : new ResourceLocation("rml", name));
    }

    public static ModuleType valueOf(ResourceLocation name) { return REGISTRY.get(name); }

    @Override
    public String toString() { return name.toString(); }
}
