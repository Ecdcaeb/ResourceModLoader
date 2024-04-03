package mods.rml.core;

import com.google.common.eventbus.Subscribe;
import mods.rml.api.config.ConfigTransformer;
import mods.rml.api.event.early.FMLBeforeStageEvent;
import mods.rml.compat.crt.CrTZenClassRegisterEvent;

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
        if (event.stage == net.minecraftforge.fml.common.LoaderState.PREINITIALIZATION){
            ConfigTransformer.handleOverride();
        }
    }

    @Subscribe
    public void registerZenClass(CrTZenClassRegisterEvent event){
        //event.register(RMLVanillaFactory.class);
    }
}
