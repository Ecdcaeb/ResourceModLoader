package mods.rml.api.mods.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mods.rml.api.announces.BeDiscovered;
import mods.rml.api.announces.PublicAPI;
import mods.rml.api.mods.ContainerHolder;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;
import rml.deserializer.JsonDeserializerException;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:54
 **/

@PublicAPI
@BeDiscovered
public class Module {
    public static final AbstractDeserializer<Module> DESERIALIZER = Deserializer.MANAGER.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "module"), Module.class, Module::decode0));
    public final ModuleType moduleType;
    public final String location;
    public final boolean forceLoaded;

    public Module(ModuleType moduleTypeIn, String locationIn, boolean forceLoadedIn) {
        this.moduleType = moduleTypeIn;
        this.location = locationIn;
        this.forceLoaded = forceLoadedIn;
    }

    public Module(ModuleType moduleType) {
        this(moduleType, moduleType.defaultLocation, false);
    }

    public static Module decode(JsonElement jsonElement) {
        try {
            return Deserializer.decode(Module.class, jsonElement);
        } catch (JsonDeserializerException e) {
            throw new RuntimeException("Could not decode a module.", e);
        }
    }

    public static Module decode0(JsonElement jsonElement) throws JsonDeserializerException{
        try {
            if (jsonElement instanceof JsonPrimitive) {
                JsonPrimitive jsonPrimitive = (JsonPrimitive) jsonElement;
                ModuleType moduleType = ModuleType.valueOf(jsonPrimitive.getAsString());
                return new Module(moduleType);
            } else if (jsonElement instanceof JsonObject) {
                JsonObject jsonObject = (JsonObject) jsonElement;
                ModuleType moduleType = ModuleType.valueOf(jsonObject.get("type").getAsString());
                String location;
                boolean forceLoaded;
                if (jsonObject.has("location")) {
                    location = jsonObject.get("location").getAsString();
                } else location = moduleType.getDefaultLocation();
                if (jsonObject.has("forceLoaded")) {
                    forceLoaded = jsonObject.get("forceLoaded").getAsBoolean();
                } else forceLoaded = false;
                return new Module(moduleType, location, forceLoaded);
            } else throw new IllegalArgumentException("unable to setup a module for " + jsonElement);
        }catch (Exception e){
            throw new JsonDeserializerException(jsonElement, e);
        }
    }

    @Override
    public String toString() {
        return "ModuleType:"+this.moduleType+"-location:"+this.location + (this.forceLoaded ? "" : "(Optional)");
    }
}
