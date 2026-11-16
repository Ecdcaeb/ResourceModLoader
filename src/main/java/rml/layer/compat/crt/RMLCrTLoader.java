package rml.layer.compat.crt;

import crafttweaker.runtime.IScriptProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.core.RMLFMLLoadingPlugin;


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
            RMLFMLLoadingPlugin.Container.LOGGER.info("Event Script Provider is injected into CrT : {}", provider_1);
            return new EventScriptProvider(provider_1);
        }
    }

    public static IScriptProvider getScriptProviders(){
        RMLScriptProvider providerCustom = new RMLScriptProvider();
        ResourceModLoader.runtime().getModules().load(ModuleType.valueOf(new ResourceLocation("rml", "mod_crt")), (context) -> {
            if (context.isExtension("zs")) {

                ResourceLocation key = context.getResourceLocation();
                providerCustom.add("rml/" + key.getNamespace() + "/" + key.getPath(), context.getBytes());
                RMLFMLLoadingPlugin.Container.LOGGER.debug("Injected {} for CrT", key);
            }
        }, RMLCrTLoader.class);
        return providerCustom;
    }

    @SubscribeEvent
    @PrivateAPI public static void inject(CrTFindingIScriptIteratorEvent event){
        event.load(getScriptProviders());
    }

    @PrivateAPI public static void registerWrappers(CrTZenClassRegisterEvent event) {

    }
}
