package rml.loader.core;

import com.google.common.eventbus.Subscribe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.mc1120.CraftTweaker;
import net.minecraftforge.fml.common.Loader;
import rml.layer.compat.crt.RMLCrTLoader;
import rml.loader.api.config.ConfigPatcher;
import rml.loader.api.event.early.FMLBeforeStageEvent;
import rml.layer.compat.crt.CrTZenClassRegisterEvent;
import net.minecraftforge.fml.common.LoaderState;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/6 23:24
 **/
@SuppressWarnings("unused")
public enum EventHandler {
    INSTANCE;

    @Subscribe
    public void beforePreInitializationEvent(FMLBeforeStageEvent event){
        switch ((LoaderState) event.stage) {
            case CONSTRUCTING: {
                RMLTransformer.Transformers.Late.initModTransformers(event.event);
                break;
            }
            case PREINITIALIZATION: {
                ConfigPatcher.Json.handleOverride();
                break;
            }
            case INITIALIZATION: {
                if (Loader.isModLoaded(CraftTweaker.MODID)) {
                    CraftTweakerAPI.tweaker.loadScript(false, "configv1");
                }
                break;
            }
            default:
        }
    }

    @Subscribe
    public void registerZenClass(CrTZenClassRegisterEvent event){
        RMLCrTLoader.registerWrappers(event);
    }
}
