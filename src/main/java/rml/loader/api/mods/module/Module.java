package rml.loader.api.mods.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.annotations.PublicAPI;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;
import rml.deserializer.JsonDeserializeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:54
 **/

@PublicAPI
@BeDiscovered(BeDiscovered.PRE_INIT)
public final class Module {
    public static final AbstractDeserializer<Module> DESERIALIZER = Deserializer.MANAGER.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), Module.class, Module::decode0));

    private final ModuleType moduleType;
    private final String location;
    private final boolean forceLoaded;
    private final List<String> dependencies;

    public Module(ModuleType moduleType, String location, boolean forceLoaded) {
        this(moduleType, location, forceLoaded, Collections.emptyList());
    }

    public Module(ModuleType moduleType, String location, boolean forceLoaded, List<String> dependencies) {
        this.moduleType = Objects.requireNonNull(moduleType, "Unknown module type");
        this.location = Objects.requireNonNull(location, "location");
        this.forceLoaded = forceLoaded;
        List<String> copy = new ArrayList<>();
        for (String dependency : dependencies) {
            if (dependency == null || dependency.isEmpty() || copy.contains(dependency)) {
                throw new IllegalArgumentException("Invalid or duplicate module dependency: " + dependency);
            }
            copy.add(dependency);
        }
        this.dependencies = Collections.unmodifiableList(copy);
    }

    public Module(ModuleType moduleType) {
        this(moduleType, Objects.requireNonNull(moduleType, "Unknown module type").getDefaultLocation(), false);
    }

    public static Module decode0(JsonElement element) throws JsonDeserializeException {
        try {
            if (element instanceof JsonPrimitive) return new Module(ModuleType.valueOf(element.getAsString()));
            JsonObject object = element.getAsJsonObject();
            ModuleType type = Objects.requireNonNull(ModuleType.valueOf(object.get("name").getAsString()), "Unknown module type");
            String location = object.has("location") ? object.get("location").getAsString() : type.getDefaultLocation();
            boolean forceLoaded = object.has("forceLoaded") && object.get("forceLoaded").getAsBoolean();
            List<String> dependencies = new ArrayList<>();
            if (object.has("requires")) {
                for (JsonElement dependency : object.getAsJsonArray("requires")) dependencies.add(dependency.getAsString());
            }
            return new Module(type, location, forceLoaded, dependencies);
        } catch (RuntimeException e) {
            throw new JsonDeserializeException(element, e);
        }
    }

    @Override
    public String toString() {
        return "ModuleType:"+this.moduleType+"-location:"+this.location + (this.forceLoaded ? "" : "(Optional)");
    }

    public ModuleType getModuleType() {
        return moduleType;
    }

    public String getLocation() {
        return location;
    }

    public boolean isForceLoaded() {
        return forceLoaded;
    }

    public List<String> getDependencies() {
        return dependencies;
    }
}
