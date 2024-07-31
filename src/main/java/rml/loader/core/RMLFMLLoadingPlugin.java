package rml.loader.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import crafttweaker.mc1120.CraftTweaker;
import dev.latvian.kubejs.KubeJS;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import org.lwjgl.Sys;
import rml.jrx.utils.ClassHelper;
import rml.loader.ResourceModLoader;
import rml.jrx.announces.BeDiscovered;
import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PrivateAPI;
import rml.jrx.announces.PublicAPI;
import rml.loader.api.RMLBus;
import rml.loader.api.world.text.RMLTextEffects;
import rml.layer.compat.crt.RMLCrTLoader;
import rml.layer.compat.kubejs.RMKKubeJs;
import rml.loader.deserialize.MCDeserializers;
import rml.loader.deserialize.RMLDeserializer;
import rml.loader.deserialize.RMLLoaders;
import rml.loader.deserialize.RMLForgeEventHandler;
import rml.loader.deserialize.craft.recipe.SimpleAnvilRecipe;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

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
    public static File source;
    public static boolean isDebug;
    @PublicAPI public static final Logger LOGGER = LogManager.getLogger(ResourceModLoader.MODID);

    public RMLFMLLoadingPlugin(){
        RMLBus.BUS.register(EventHandler.INSTANCE);
    }

    public static void makeFMLCorePluginContainsFMLMod(File file){
        String name = file.getName();
        CoreModManager.getIgnoredMods().remove(name);
        CoreModManager.getReparseableCoremods().add(name);
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "rml.loader.core.RMLTransformer"
        };
    }
    @Override
    public String getModContainerClass() {
        return "rml.loader.core.RMLFMLLoadingPlugin$Container";
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
        if (arguments.containsKey("--rml.debug")){
            isTestingLaunching = arguments.containsKey("rml.debug");
        }
        if (isDebug){
            arguments.forEach((key, value) -> LOGGER.info("{} | {}", key, value));
        }
        ASMUtil.saveTransformedClass = isDebug;
    }
    
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
    @PublicAPI
    @SuppressWarnings("unused")
    public static class Container extends DummyModContainer{
        @PublicAPI public static ModContainer INSTANCE;
        @PublicAPI public static final Logger LOGGER = RMLFMLLoadingPlugin.LOGGER;
        @PrivateAPI public Container(){
            super(new ModMetadata());
            ModMetadata metadata = this.getMetadata();
            metadata.modId = ResourceModLoader.MODID;
            metadata.name = "Resource Mod Loader";
            metadata.authorList.add("Hileb");
            metadata.version = ResourceModLoader.VERSION;
            metadata.credits = "\n" +
                    "       Idealland - they provided this framework for enviroment.\n" +
                    "       zfms4188  - support the RML at CraftTweaker compat! \n";
            metadata.description = "a modloader which load mods from resource packs.(in mods/)";
            metadata.url = "https://github.com/Ecdcaeb/ResourceModLoader";
            metadata.updateJSON = "https://raw.githubusercontent.com/Ecdcaeb/ResourceModLoader/main/docs/updates.json";
            metadata.logoFile ="assets/rml/icon.png";
            INSTANCE = this;
        }
        @Override
        @PrivateAPI public boolean registerBus(EventBus bus, LoadController controller) {
            bus.register(this);
            INSTANCE = Loader.instance().getIndexedModList().get("rml");
            return true;
        }
        @Subscribe
        @SuppressWarnings("unused")
        @PrivateAPI public void preInit(FMLPreInitializationEvent event){
            MinecraftForge.EVENT_BUS.register(RMLForgeEventHandler.class);
            MinecraftForge.EVENT_BUS.register(SimpleAnvilRecipe.class);
            if (FMLLaunchHandler.side() == Side.CLIENT){
                MinecraftForge.EVENT_BUS.register(RMLTextEffects.ChangeModClickAction.class);
            }
            if (Loader.isModLoaded(KubeJS.MOD_ID)){
                MinecraftForge.EVENT_BUS.register(RMKKubeJs.class);
            }
            if (Loader.isModLoaded(CraftTweaker.MODID)){
                MinecraftForge.EVENT_BUS.register(RMLCrTLoader.class);
            }
            RMLForgeEventHandler.preInit(event);
            RMLLoaders.MissingRemap.load();
        }
        @Subscribe
        @PrivateAPI public void construct(FMLConstructionEvent event){
            RMLForgeEventHandler.construct(event);
            ClassHelper.forceInit(RMLDeserializer.class);
            ClassHelper.forceInit(MCDeserializers.class);
        }
        @Subscribe
        @SuppressWarnings("unused")
        @PrivateAPI public void postInit(FMLPostInitializationEvent event){
            RMLForgeEventHandler.postInit(event);
        }

        @Subscribe
        @SuppressWarnings("unused")
        @PrivateAPI public void postInit(FMLLoadCompleteEvent event){
            if (isTestingLaunching) FMLCommonHandler.instance().exitJava(0 ,false);
        }
        @Override
        public File getSource() {
            return source;
        }

        @Override
        @PrivateAPI public Class<?> getCustomResourcePackClass()
        {
            if(getSource() == null) return null;
            try
            {
                return getSource().isDirectory() ? Class.forName("net.minecraftforge.fml.client.FMLFolderResourcePack", true, getClass().getClassLoader()) : Class.forName("net.minecraftforge.fml.client.FMLFileResourcePack", true, getClass().getClassLoader());
            }
            catch (ClassNotFoundException e)
            {
                return null;
            }
        }
    }
}
