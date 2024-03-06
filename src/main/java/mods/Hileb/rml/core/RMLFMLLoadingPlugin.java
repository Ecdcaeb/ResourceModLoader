package mods.Hileb.rml.core;

import com.cleanroommc.groovyscript.GroovyScript;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import crafttweaker.mc1120.CraftTweaker;
import dev.latvian.kubejs.KubeJS;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.RMLBus;
import mods.Hileb.rml.compat.crt.RMLCrTLoader;
import mods.Hileb.rml.compat.groovyscript.GroovyScriptHandler;
import mods.Hileb.rml.compat.kubejs.RMKKubeJs;
import mods.Hileb.rml.deserialize.RMLForgeEventHandler;
import mods.Hileb.rml.deserialize.RMLSerializeLoader;
import mods.Hileb.rml.deserialize.craft.recipe.SimpleAnvilRecipe;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
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
    public RMLFMLLoadingPlugin(){
        RMLBus.BUS.register(EventHandler.INSTANCE);
    }
    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "mods.Hileb.rml.core.RMLTransformer"
        };
    }
    @Override
    public String getModContainerClass() {
        return "mods.Hileb.rml.core.RMLFMLLoadingPlugin$Container";
    }
    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }
    @Override
    public void injectData(Map<String, Object> data) {
        source=(File) data.get("coremodLocation");
        ASMUtil.gameDir=(File)data.get("mcLocation");
    }
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
    @PublicAPI
    @SuppressWarnings("unused")
    public static class Container extends DummyModContainer{
        @PublicAPI public static Container INSTANCE;
        @PublicAPI public static final Logger LOGGER= LogManager.getLogger(ResourceModLoader.MODID);
        @PrivateAPI public Container(){
            super(new ModMetadata());
            ModMetadata metadata=this.getMetadata();
            metadata.modId=ResourceModLoader.MODID;
            metadata.name="Resource Mod Loader";
            metadata.authorList.add("Hileb");
            metadata.version=ResourceModLoader.VERSION;
            metadata.credits="\n" +
                    "       Idealland - they provided this framework for enviroment.\n";
            metadata.description="a modloader which load mods from resource packs.(in mods/)";
            metadata.url="https://github.com/Ecdcaeb/ResourceModLoader";
            metadata.logoFile="assets/rml/icon.png";
            INSTANCE=this;
            RMLBus.BUS.register(new RMLBusHandler());
        }
        @Override
        @PrivateAPI public boolean registerBus(EventBus bus, LoadController controller) {
            bus.register(this);
            return true;
        }
        @Subscribe
        @SuppressWarnings("unused")
        @PrivateAPI public void preInit(FMLPreInitializationEvent event){
            MinecraftForge.EVENT_BUS.register(RMLForgeEventHandler.class);
            MinecraftForge.EVENT_BUS.register(SimpleAnvilRecipe.class);
            if (Loader.isModLoaded(KubeJS.MOD_ID)){
                MinecraftForge.EVENT_BUS.register(RMKKubeJs.class);
            }
            if (Loader.isModLoaded(CraftTweaker.MODID)){
                MinecraftForge.EVENT_BUS.register(RMLCrTLoader.class);
            }
            RMLForgeEventHandler.preInit(event);
        }
        @Subscribe
        @PrivateAPI public void construct(FMLConstructionEvent event){
            RMLSerializeLoader.MissingRemap.load();
            if (Loader.isModLoaded(GroovyScript.ID)){
                RMLBus.BUS.register(GroovyScriptHandler.INSTANCE);
            }
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
            try
            {
                return getSource().isDirectory() ? Class.forName("net.minecraftforge.fml.client.FMLFolderResourcePack", true, getClass().getClassLoader()) : Class.forName("net.minecraftforge.fml.client.FMLFileResourcePack", true, getClass().getClassLoader());
            }
            catch (ClassNotFoundException e)
            {
                return null;
            }
        }
        public static class RMLBusHandler{

        }
    }
}
