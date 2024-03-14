package mods.Hileb.rml.compat.crt;

import crafttweaker.runtime.CrTTweaker;
import crafttweaker.runtime.IScriptProvider;
import crafttweaker.runtime.ITweaker;
import crafttweaker.runtime.providers.ScriptProviderCustom;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 22:45
 **/
public class RMLCrTLoader {
    @PrivateAPI
    public static final Field scriptProvider;
    static {
        try {
            scriptProvider= CrTTweaker.class.getDeclaredField("scriptProvider");
            scriptProvider.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * public void setScriptProvider(IScriptProvider provider) {
     *         this.scriptProvider = provider;
     *         RMLCrTLoader.inject(this);
     *     }
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
    @SubscribeEvent
    @PrivateAPI public static void inject(CrTFindingIScriptIteratorEvent event){
        for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
            if (containerHolder.modules.contains(ContainerHolder.Modules.MOD_CRT)){
                final ModContainer modContainer = containerHolder.container;
                Loader.instance().setActiveModContainer(modContainer);


                ScriptProviderCustom providerCustom=new ScriptProviderCustom(modContainer.getModId());
                FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/crt",
                        (root, file) ->
                        {
                            String relative = root.relativize(file).toString();
                            if (!"zs".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                return;

                            String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                            ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                            name = "rml/"+key.getResourceDomain()+"/"+name;
                            name = name.replace('/', '$');
                            try{
                                byte[] fileBytes = FileHelper.getByteSource(file).read();

                                providerCustom.add(name,fileBytes);

                                RMLFMLLoadingPlugin.Container.LOGGER.info("Injected {} for CrT",key);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                event.load(providerCustom);

                Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
            }
        }
    }
}
