package mods.Hileb.rml.api.event;

import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.RMLBus;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/9 23:55
 *
 * use {@link mods.Hileb.rml.api.event.early.FMLBeforeStageEvent}
 **/
@Deprecated
@PublicAPI
public class FMLBeforePreInitEvent {
    private FMLBeforePreInitEvent(){}
    @SuppressWarnings("unused")
    @PrivateAPI public static void post(){
        RMLBus.BUS.post(new FMLBeforePreInitEvent());
    }
}
