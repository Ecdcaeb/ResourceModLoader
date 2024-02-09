package mods.Hileb.rml.compat.fml;

import mods.Hileb.rml.api.config.ConfigTransformer;
import mods.Hileb.rml.api.event.FMLBeforePreInitEvent;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/10 0:13
 **/
public class RMLFMLHooks {
    @SuppressWarnings("unused")
    public static void beforeFMLBusEventSending(FMLEvent event){
        if (event instanceof FMLPreInitializationEvent){
            ConfigTransformer.handleOverride();
            FMLBeforePreInitEvent.post();
        }
    }
}
