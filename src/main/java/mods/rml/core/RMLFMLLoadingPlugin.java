package mods.rml.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import crafttweaker.mc1120.CraftTweaker;
import dev.latvian.kubejs.KubeJS;
import mods.rml.ResourceModLoader;
import mods.rml.api.announces.EarlyClass;
import mods.rml.api.announces.PrivateAPI;
import mods.rml.api.announces.PublicAPI;
import mods.rml.api.RMLBus;
import mods.rml.api.text.ChangeMod;
import mods.rml.compat.crt.RMLCrTLoader;
import mods.rml.compat.kubejs.RMKKubeJs;
import mods.rml.deserialize.RMLDeserializeLoader;
import mods.rml.deserialize.RMLForgeEventHandler;
import mods.rml.deserialize.craft.recipe.SimpleAnvilRecipe;
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
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
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
    public static File source;
    public static boolean isDebug;
    @PublicAPI public static final Logger LOGGER = LogManager.getLogger(ResourceModLoader.MODID);

    public RMLFMLLoadingPlugin(){
        RMLBus.BUS.register(EventHandler.INSTANCE);
    }
    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "mods.rml.core.RMLTransformer"
        };
    }
    @Override
    public String getModContainerClass() {
        return "mods.rml.core.RMLFMLLoadingPlugin$Container";
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

        //start args>>
        //read the args :
        Map<String,String> arguments = (Map<String,String>) Launch.blackboard.get("launchArgs");
        if (arguments.containsKey("rml.debug")){
            isDebug = Boolean.parseBoolean(arguments.get("rml.debug"));
        }
        //apply the args :
        //TODO: handle all pre args.
        isDebug = true;//Boolean.parseBoolean(System.getProperty("rml.debug", "false"));
        ASMUtil.saveTransformedClass = isDebug;
        //>>end args;

        //debug:
        if (isDebug){
            LOGGER.warn("inject data: ");
            for(String s : data.keySet()){
                LOGGER.warn("{} : {}",s, String.valueOf(data.get(s)));
            }
            LOGGER.warn("args: ");
            for(Map.Entry<String,String> s : arguments.entrySet()){
                LOGGER.warn(s.getKey()+" >> "+s.getValue());
            }
            ASMUtil.saveTransformedClass = true;
        }
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
            ModMetadata metadata=this.getMetadata();
            metadata.modId=ResourceModLoader.MODID;
            metadata.name="Resource Mod Loader";
            metadata.authorList.add("Hileb");
            metadata.version=ResourceModLoader.VERSION;
            metadata.credits="\n" +
                    "       Idealland - they provided this framework for enviroment.\n" +
                    "       zfms4188  - support the RML at CraftTweaker compat! \n";
            metadata.description="a modloader which load mods from resource packs.(in mods/)";
            metadata.url="https://github.com/Ecdcaeb/ResourceModLoader";
            metadata.logoFile="assets/rml/icon.png";
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
                MinecraftForge.EVENT_BUS.register(ChangeMod.ChangeModAction.class);
            }
            if (Loader.isModLoaded(KubeJS.MOD_ID)){
                MinecraftForge.EVENT_BUS.register(RMKKubeJs.class);
            }
            if (Loader.isModLoaded(CraftTweaker.MODID)){
                MinecraftForge.EVENT_BUS.register(RMLCrTLoader.class);
            }
            RMLForgeEventHandler.preInit(event);
            RMLDeserializeLoader.MissingRemap.load();
        }
        @Subscribe
        @PrivateAPI public void construct(FMLConstructionEvent event){
        }
        @Subscribe
        @SuppressWarnings("unused")
        @PrivateAPI public void postInit(FMLPostInitializationEvent event){
            RMLForgeEventHandler.postInit(event);
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
