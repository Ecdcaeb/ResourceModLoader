package mods.rml.api.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mods.rml.api.announces.PublicAPI;
import net.minecraftforge.fml.common.ModContainer;

import java.util.Arrays;
import java.util.HashMap;

@PublicAPI
public class ContainerHolder {
    public final ModContainer container;
    public final HashMap<ModuleType,Module> modules;
    public ContainerHolder(ModContainer container, Module[] modules){
        this.container = container;
        this.modules = new HashMap<>();
        for(Module module : modules){
            this.modules.put(module.moduleType, module);
        }
    }

    public ModContainer getContainer() {
        return container;
    }

    public HashMap<ModuleType,Module> getModules() {
        return modules;
    }

    public boolean hasModule(ModuleType module){
        return modules.containsKey(module);
    }

    public ContainerHolder(ModContainer container){
        this(container, ModuleType.getAllForDefault());
    }

    public enum ModuleType {
        CONFIG_OVERRIDE("config/override", false),
        CONFIG_REDEFAULT("config/redefault", false),
        MOD_CRT("crt", false),
        FUNCTIONS("functions", false),
        MOD_KUBEJS("kubejs", false),
        LOOT_TABLES("loot_tables", false),
        ORE_DIC("ore_dic", false),
        REGISTRY_REMAP("registry/remap", false),
        MOD_GROOVY_SCRIPT("groovy_script", false),
        CUSTOM_VILLAGERS("villages", false),
        SPLASH_TEXT("text/splash_text.txt", true);

        public final boolean isFile;
        public final String defaultLocation;
        ModuleType(String defaultLocationIn, boolean isFileIn){
            this.defaultLocation = defaultLocationIn;
            this.isFile = isFileIn;
        }
        public String getDefaultLocation(){
            return defaultLocation;
        }

        public boolean isFile() {
            return isFile;
        }
        public static Module[] getAllForDefault(){
            return Arrays.stream(ModuleType.values()).map(Module::new).toArray(Module[]::new);
//            ModuleType[] types = ModuleType.values();
//            Module[] modules = new Module[types.length];
//            for(int i = 0; i < modules.length; i++){
//                modules[i] = new Module(types[i]);
//            }
//            return modules;
        }
    }

    public static class Module{
        public final ModuleType moduleType;
        public final String location;
        public final boolean forceLoaded;

        public Module(ModuleType moduleTypeIn, String locationIn, boolean forceLoadedIn){
            this.moduleType = moduleTypeIn;
            this.location = locationIn;
            this.forceLoaded = forceLoadedIn;
        }

        public Module(ModuleType moduleType){
            this(moduleType, moduleType.defaultLocation, false);
        }

        public static Module decode(JsonElement jsonElement){
            if (jsonElement instanceof JsonPrimitive){
                JsonPrimitive jsonPrimitive = (JsonPrimitive) jsonElement;
                ModuleType moduleType = ModuleType.valueOf(jsonPrimitive.getAsString());
                return new Module(moduleType);
            }
            else if (jsonElement instanceof JsonObject){
                JsonObject jsonObject = (JsonObject) jsonElement;
                ModuleType moduleType = ModuleType.valueOf(jsonObject.get("type").getAsString());
                String location;
                boolean forceLoaded;
                if (jsonObject.has("location")){
                    location = jsonObject.get("location").getAsString();
                }else location = moduleType.getDefaultLocation();
                if (jsonObject.has("forceLoaded")){
                    forceLoaded = jsonObject.get("forceLoaded").getAsBoolean();
                }else forceLoaded = false;
                return new Module(moduleType, location, forceLoaded);
            }
            else throw new IllegalArgumentException("unable to setup a module for " + jsonElement);
        }
    }

    @FunctionalInterface
    public interface ModuleConsumer{
        void accept(ModuleType module, ContainerHolder containerHolder);
    }
}
