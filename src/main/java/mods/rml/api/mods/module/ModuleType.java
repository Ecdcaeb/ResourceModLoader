package mods.rml.api.mods.module;

import mods.rml.api.announces.PublicAPI;
import mods.rml.api.mods.module.plugin.ICustomModulePlugin;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:54
 **/

@PublicAPI
public enum ModuleType implements IModuleType {
    CONFIG_OVERRIDE("config/override", false),
    CONFIG_REDEFAULT("config/redefault", false),
    CONFIG_DEFINE("config/define", false),
    MOD_CRT("crt", false),
    FUNCTIONS("functions", false),
    MOD_KUBEJS("kubejs", false),
    LOOT_TABLES("loot_tables", false),
    ORE_DIC("ore_dic", false),
    REGISTRY_REMAP("registry/remap", false),
    MOD_GROOVY_SCRIPT("groovy_script", false),//TODO
    CUSTOM_VILLAGERS("villages", false),
    SPLASH_TEXT("text/splash_text.txt", true),
    STRUCTURE("structures", false),//TODO
    DIMENSTION("dimension", false);//TODO

    public final boolean isFile;
    public final String defaultLocation;

    ModuleType(String defaultLocationIn, boolean isFileIn) {
        this.defaultLocation = defaultLocationIn;
        this.isFile = isFileIn;
    }

    ModuleType(IModuleType iModuleType) {
        this.defaultLocation = iModuleType.getDefaultLocation();
        this.isFile = iModuleType.isFile();
    }

    @Override
    public String getName() {
        return this.name();
    }

    @Override
    public String getDefaultLocation() {
        return defaultLocation;
    }

    @Override
    public boolean isFile() {
        return isFile;
    }

    @Override
    public void consume(ModuleType moduleType) {

    }

    public static Module[] getAllForDefault() {
        return Arrays.stream(ModuleType.values()).map(Module::new).toArray(Module[]::new);
    }

    public static class PluginManager{
        public static final HashMap<String, ICustomModulePlugin> PLUGINS = new HashMap<>();

        public static void loadPlugins(ASMDataTable asmDataTable){
            for(ASMDataTable.ASMData asmData : asmDataTable.getAll("mods/rml/api/mods/module/plugin/ICustomModulePlugin")){
                try {
                    ICustomModulePlugin plugin = (ICustomModulePlugin) Class.forName(asmData.getClassName()).getConstructor().newInstance();
                    PLUGINS.put(Objects.requireNonNull(plugin.getPluginName(), "plugin name could not be null!!! @"+asmData.getClassName()), plugin);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException | ClassNotFoundException e) {
                    throw new RuntimeException("plugin "+ asmData.getClassName() + " could not be created. There must be a public non-arg constructor here for ICustomModulePlugin" ,e);
                }
            }
        }

        public static void runPlugins(){
            Set<String> modules = Arrays.stream(ModuleType.values()).map(Enum::name).collect(Collectors.toSet());
            for(ICustomModulePlugin plugin : PLUGINS.values()){
                for(IModuleType type : plugin.createModuleTypes()){
                    if (modules.contains(type.getName())){
                        throw new RuntimeException("Could not register ModuleType", new IllegalArgumentException("Module Could not Register twice, @"+type.getName()+"@"+plugin.getPluginName()));
                    }else {
                        type.consume(EnumHelper.addEnum(ModuleType.class, type.getName(), new Class[]{IModuleType.class}, type));
                        modules.add(type.getName());
                    }
                }
            }
        }
    }
}
