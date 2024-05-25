package mods.rml.core;

import com.google.common.eventbus.Subscribe;
import mods.rml.api.config.ConfigPatcher;
import mods.rml.api.event.early.FMLBeforeStageEvent;
import mods.rml.api.mods.module.ModuleType;
import mods.rml.compat.crt.CrTZenClassRegisterEvent;
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
            ModuleType.PluginManager.loadPlugins((ASMDataTable) event.event[1]);
            RMLTransformer.Transformers.Late.initModTransformers(event.event);
        }
    }
}
