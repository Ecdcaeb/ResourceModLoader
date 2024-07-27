package rml.loader.core;

import com.google.common.eventbus.Subscribe;
import rml.loader.api.config.ConfigPatcher;
import rml.loader.api.event.early.FMLBeforeStageEvent;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.compat.crt.CrTZenClassRegisterEvent;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

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
            ConfigPatcher.Json.handleOverride();
        }
    }

    @Subscribe
    public void registerZenClass(CrTZenClassRegisterEvent event){

    }

    public void beforeConstruction(FMLBeforeStageEvent event){
        if (event.stage == LoaderState.CONSTRUCTING){
            RMLTransformer.Transformers.Late.initModTransformers(event.event);
            ModuleType.loadPlugins((ASMDataTable) event.event[1]);
        }
    }
}
