package rml.loader.core;

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
public class RMLBusHandler {

    public static void beforePreInitializationEvent(FMLBeforeStageEvent event){
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
                break;
            }
            default:
        }
    }

    public static void registerZenClass(CrTZenClassRegisterEvent event){
        RMLCrTLoader.registerWrappers(event);
    }
}
