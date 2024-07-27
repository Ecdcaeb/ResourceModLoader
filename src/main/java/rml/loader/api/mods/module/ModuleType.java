package rml.loader.api.mods.module;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;
import rml.jrx.announces.PrivateAPI;
import rml.jrx.announces.PublicAPI;
import rml.jrx.registry.NamedRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:54
 **/

@PublicAPI
public class ModuleType implements IModuleType {
    public static final NamedRegistry<ModuleType> REGISTRY = new NamedRegistry<>();
    public static final ModuleType CONFIG_OVERRIDE = register("CONFIG_OVERRIDE ","config/override", false);
    public static final ModuleType CONFIG_REDEFAULT = register("CONFIG_REDEFAULT","config/redefault", false);
    public static final ModuleType CONFIG_DEFINE = register("CONFIG_DEFINE","config/define", false);
    public static final ModuleType MOD_CRT = register("MOD_CRT","crt", false);
    public static final ModuleType FUNCTIONS = register("FUNCTIONS","functions", false);
    public static final ModuleType MOD_KUBEJS = register("MOD_KUBEJS","kubejs", false);
    public static final ModuleType LOOT_TABLES = register("LOOT_TABLES","loot_tables", false);
    public static final ModuleType ORE_DIC = register("ORE_DIC","ore_dic", false);
    public static final ModuleType REGISTRY_REMAP = register("REGISTRY_REMAP","registry/remap", false);
    public static final ModuleType MOD_GROOVY_SCRIPT = register("MOD_GROOVY_SCRIPT","groovy_script", false);//TODO
    public static final ModuleType CUSTOM_VILLAGERS = register("CUSTOM_VILLAGERS","villages", false);
    public static final ModuleType SPLASH_TEXT = register("SPLASH_TEXT","text/splash_text.txt", true);
    public static final ModuleType STRUCTURE = register("STRUCTURE","structures", false);//TODO
    public static final ModuleType DIMENSION = register("DIMENSION", "dimension", false);//TODO

    private static ModuleType register(String name, String defaultLocation, boolean isFile){
        ResourceLocation location = new ResourceLocation("rml", name);
        return new ModuleType(location, defaultLocation, isFile);
    }

    public ResourceLocation name;
    public final boolean isFile;
    public final String defaultLocation;

    public ModuleType(ResourceLocation name, String defaultLocationIn, boolean isFileIn) {
        this.name = name;
        this.defaultLocation = defaultLocationIn;
        this.isFile = isFileIn;
        REGISTRY.register(name, this);
    }

    public ModuleType(IModuleType iModuleType) {
        this(iModuleType.getName(), iModuleType.getDefaultLocation(), iModuleType.isFile());
    }


    @Override
    public ResourceLocation getName() {
        return name;
    }

    @Override
    public String getDefaultLocation() {
        return defaultLocation;
    }

    @Override
    public boolean isFile() {
        return isFile;
    }

    public static Module[] getAllForDefault() {
        return Arrays.stream(ModuleType.values()).map(Module::new).toArray(Module[]::new);
    }

    @PrivateAPI
    public static void loadPlugins(ASMDataTable asmDataTable) {
        for (ASMDataTable.ASMData asmData : asmDataTable.getAll("rml/loader/api/mods/module/IModuleType")) {
            try {
                IModuleType plugin = (IModuleType) Class.forName(asmData.getClassName()).getConstructor().newInstance();
                new ModuleType(plugin);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException("plugin " + asmData.getClassName() + " could not be created. There must be a public non-arg constructor here for ICustomModulePlugin", e);
            }
        }
    }

    public static ModuleType[] values(){
        return REGISTRY.keys().stream().map(REGISTRY::get).toArray(ModuleType[]::new);
    }

    @PrivateAPI
    public static ModuleType valueOf(String s){
        if (s.indexOf(':') >= 0) return valueOf(new ResourceLocation(s));
        else return valueOf(new ResourceLocation("rml", s));
    }

    public static ModuleType valueOf(ResourceLocation resourceLocation){
        return REGISTRY.get(resourceLocation);
    }
}
