package mods.Hileb.rml.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import dev.latvian.kubejs.KubeJS;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.compat.kubejs.RMKKubeJs;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:37
 **/
@IFMLLoadingPlugin.Name(ResourceModLoader.MODID)
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class RMLFMLPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "mods.Hileb.rml.core.RMLLoaderTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return "mods.Hileb.rml.core.RMLFMLPlugin$Container";
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
    public static class Container extends DummyModContainer{
        public static Container INSTANCE;
        public static final Logger LOGGER= LogManager.getLogger(ResourceModLoader.MODID);
        public static final ArtifactVersion VERSION=new DefaultArtifactVersion(ResourceModLoader.MODID,ResourceModLoader.VERSION);
        public Container(){
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
            INSTANCE=this;
        }

        @Override
        public boolean registerBus(EventBus bus, LoadController controller) {
            bus.register(this);
            return true;
        }

        @Subscribe
        @SuppressWarnings("unused")
        public void preInit(FMLPreInitializationEvent event){
            if (Loader.isModLoaded(KubeJS.MOD_ID)){
                MinecraftForge.EVENT_BUS.register(RMKKubeJs.class);
            }
        }
    }
}
