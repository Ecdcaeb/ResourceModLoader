package mods.rml.core;

import com.google.common.eventbus.Subscribe;
import mods.rml.api.config.ConfigTransformer;
import mods.rml.api.event.early.FMLBeforeStageEvent;
import mods.rml.compat.crt.CrTZenClassRegisterEvent;
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
        if (event.stage == LoaderState.PREINITIALIZATION){
            ConfigTransformer.handleOverride();
        }
    }

    @Subscribe
    public void registerZenClass(CrTZenClassRegisterEvent event){
        //event.register(RMLVanillaFactory.class);
    }

    public void beforeConstruction(FMLBeforeStageEvent event){
        if (event.stage == LoaderState.CONSTRUCTING){
            RMLTransformer.Transformers.Late.initModTransformers(event.event);
        }
    }
}
