package rml.layer.compat.crt;

import crafttweaker.runtime.IScriptProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.FilenameUtils;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.utils.file.FileHelper;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.core.RMLFMLLoadingPlugin;
import rml.loader.api.config.v2.config.ConfigUtils;
import rml.loader.api.config.v2.config.elements.*;

import java.io.IOException;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 22:45
 **/
public class RMLCrTLoader {
    /**
     * public void setScriptProvider(IScriptProvider provider) {
     *         provider = RMLCrTLoader.inject(provider);
     *         ...
     *
     * **/
    @SuppressWarnings("unused")
    @PrivateAPI public static IScriptProvider inject(IScriptProvider provider_1){
        if(provider_1 instanceof EventScriptProvider){
            return provider_1;
        } else {
            RMLFMLLoadingPlugin.Container.LOGGER.info("Event Script Provider is injected into CrT:"+provider_1.toString());
            return new EventScriptProvider(provider_1);
        }
    }

    public static IScriptProvider getScriptProviders(){
        RMLScriptProvider providerCustom = new RMLScriptProvider();
        ResourceModLoader.loadModuleFindAssets(ModuleType.valueOf(new ResourceLocation("rml", "mod_crt")), (containerHolder, module, root, file) -> {
            String relative = root.relativize(file).toString();
            if (!"zs".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                return;

            String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
            ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
            name = "rml/"+key.getNamespace()+"/"+name;
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

    @PrivateAPI public static void registerWrappers(CrTZenClassRegisterEvent event) {
        event.register(ConfigUtils.class);
        event.register(ConfigBoolean.class);
        event.register(ConfigBooleanArray.class);
        event.register(ConfigGroup.class);
        event.register(ConfigDouble.class);
        event.register(ConfigDoubleArray.class);
        event.register(ConfigElement.class);
        event.register(ConfigEnum.class);
        event.register(ConfigInt.class);
        event.register(ConfigIntArray.class);
        event.register(ConfigMap.class);
        event.register(ConfigPrimitive.class);
        event.register(ConfigRangedDouble.class);
        event.register(ConfigRangedInt.class);
        event.register(ConfigString.class);
        event.register(ConfigStringArray.class);
        event.register(ConfigMap.HashDataMap.class);
    }
}
