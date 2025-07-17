package rml.loader.core;

import crafttweaker.annotations.ZenRegister;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rml.layer.compat.crt.CrTZenClassRegisterEvent;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.event.early.FMLBeforeStageEvent;
import rml.loader.api.reflection.jvm.FieldAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.api.utils.ObjectHelper;
import rml.loader.ResourceModLoader;
import rml.loader.deserialize.RMLForgeEventHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:37
 **/
@EarlyClass
@PrivateAPI
@IFMLLoadingPlugin.Name(ResourceModLoader.MODID)
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class RMLFMLLoadingPlugin implements IFMLLoadingPlugin {
    private static boolean isTestingLaunching = false;
    public static File source = null;
    public static File developingPack = null;
    public static boolean isDebug = false;
    @PublicAPI public static final Logger LOGGER = LogManager.getLogger(ResourceModLoader.MODID);

    public RMLFMLLoadingPlugin(){
        FMLBeforeStageEvent.BUS.register(RMLBusHandler::beforePreInitializationEvent);
        CrTZenClassRegisterEvent.BUS.register(RMLBusHandler::registerZenClass);
    }

    public static void makeFMLCorePluginContainsFMLMod(File file){
        String name = file.getName();
        CoreModManager.getIgnoredMods().remove(name);
        CoreModManager.getReparseableCoremods().add(name);
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "rml.loader.core.RMLTransformer",
                "rml.loader.api.config.v2.config.ConfigUtils.ClassProvider"
        };
    }
    @Override
    public String getModContainerClass() {
        return null;
    }
    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }


    @Override
    public void injectData(Map<String, Object> data) {
        source = (File) data.get("coremodLocation");
        ASMUtil.gameDir = Launch.minecraftHome;
        makeFMLCorePluginContainsFMLMod(source);
        //start args>>
        //read the args :
        @SuppressWarnings("unchecked")
        Map<String,String> arguments = (Map<String,String>) Launch.blackboard.get("launchArgs");
        if (arguments.containsKey("--rml.debug")){
            isDebug = Boolean.parseBoolean(arguments.get("--rml.debug"));
        }
        if (arguments.containsKey("--rml.test")){
            isTestingLaunching = arguments.containsKey("--rml.test");
        }
        if (arguments.containsKey("--rml.dev.location")){
            developingPack = new File(Launch.minecraftHome, arguments.get("--rml.dev.location"));
        }
        if (isDebug){
            arguments.forEach((key, value) -> LOGGER.info("{} | {}", key, value));
        }
        ASMUtil.saveTransformedClass = isDebug;



        @SuppressWarnings("all")
        List<Object> coremodList = (List<Object>) data.get("coremodList");

        if (coremodList != null){
            try {
                Class<?> CoreModManager$FMLPluginWrapper = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager$FMLPluginWrapper");
                FieldAccessor<String, Object> name = ReflectionHelper.getFieldAccessor(CoreModManager$FMLPluginWrapper, "name");
                for(Object plugin : coremodList){
                    if ("GroovyScript-Core".equals(name.get(ObjectHelper.dynamic_cast(CoreModManager$FMLPluginWrapper, plugin)))) {
                        RMLTransformer.Transformers.initGroovyScriptTransformer();
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Mod(modid = ResourceModLoader.MODID, version = ResourceModLoader.VERSION, acceptableRemoteVersions = "*")
    public static class Container{

        @PublicAPI public static ModContainer INSTANCE = Loader.instance().getIndexedModList().get(ResourceModLoader.MODID);
        @PublicAPI public static final Logger LOGGER = RMLFMLLoadingPlugin.LOGGER;

//        @PrivateAPI public Container(){
//            super(new ModMetadata());
//            ModMetadata metadata = this.getMetadata();
//            metadata.modId = ResourceModLoader.MODID;
//            metadata.name = "Resource Mod Loader";
//            metadata.authorList.add("Hileb");
//            metadata.version = ResourceModLoader.VERSION;
//            metadata.credits = "\n" +
//                    "       Idealland - they provided this framework for enviroment.\n" +
//                    "       zfms4188  - support the RML at CraftTweaker compat! \n";
//            metadata.description = "a modloader which load mods from resource packs.(in mods/)";
//            metadata.url = "https://github.com/Ecdcaeb/ResourceModLoader";
//            metadata.updateJSON = "https://raw.githubusercontent.com/Ecdcaeb/ResourceModLoader/main/docs/updates.json";
//            metadata.logoFile ="assets/rml/icon.png";
//            INSTANCE = this;
//        }


        @Mod.EventHandler
        @PrivateAPI public void preInit(FMLPreInitializationEvent event){
            RMLForgeEventHandler.preInit(event);
        }

        @Mod.EventHandler
        @PrivateAPI public void construct(FMLConstructionEvent event){
            RMLForgeEventHandler.construct(event);
            event.getASMHarvestedData().getAll(ZenRegister.class.getName()).stream().filter(asmData -> asmData.getClassName().startsWith("youyihj")).forEach(asmData -> System.out.printf("ZenRegister "+ asmData.getClassName()));
        }

        @Mod.EventHandler
        @PrivateAPI public void postInit(FMLPostInitializationEvent event){
            RMLForgeEventHandler.postInit(event);
        }

        @Mod.EventHandler
        public void init(FMLInitializationEvent event){
            RMLForgeEventHandler.onInit(event);
        }

    }
}
