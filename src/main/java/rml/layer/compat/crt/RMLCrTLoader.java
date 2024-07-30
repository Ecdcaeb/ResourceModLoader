package rml.layer.compat.crt;

import crafttweaker.runtime.IScriptProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.FilenameUtils;
import rml.jrx.announces.PrivateAPI;
import rml.jrx.utils.file.FileHelper;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.core.RMLFMLLoadingPlugin;

import java.io.IOException;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 22:45
 **/
public class RMLCrTLoader {
    /**
     * public void setScriptProvider(IScriptProvider provider) {
     *         this.scriptProvider = provider;
     *         ...
     *
     * **/
    @SuppressWarnings("unused")
    @PrivateAPI public static IScriptProvider inject(IScriptProvider provider_1){
        if(provider_1 instanceof EventScriptProvider){
            return provider_1;
        }else{
            RMLFMLLoadingPlugin.Container.LOGGER.info("Event Script Provider is injected into CrT:"+provider_1.toString());
            return new EventScriptProvider(provider_1);
        }
    }

    public static IScriptProvider getScriptProviders(){
        RMLScriptProvider providerCustom = new RMLScriptProvider();
        ResourceModLoader.loadModuleFindAssets(CrTModule.TYPE, (containerHolder, root, file) -> {
            String relative = root.relativize(file).toString();
            if (!"zs".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                return;

            String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
            ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
            name = "rml/"+key.getResourceDomain()+"/"+name;
            try{
                byte[] fileBytes = FileHelper.getByteSource(file).read();

                providerCustom.add(name, fileBytes);

                RMLFMLLoadingPlugin.Container.LOGGER.info("Injected {} for CrT",key);
            } catch (IOException e) {
                throw new RuntimeException("IOException when RML loading " + file, e);
            }
        });
        return providerCustom;
    }

    @SubscribeEvent
    @PrivateAPI public static void inject(CrTFindingIScriptIteratorEvent event){
        event.load(getScriptProviders());
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/7/30 10:15
     **/
    public static class CrTModule{

        public static ModuleType TYPE = new ModuleType(new ResourceLocation("rml", "mod_crt"), "crt", false);
    }
}
