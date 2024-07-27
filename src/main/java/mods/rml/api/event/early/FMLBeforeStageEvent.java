package mods.rml.api.event.early;

import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PublicAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/6 23:14
 **/
@EarlyClass
@PublicAPI
public class FMLBeforeStageEvent {
    public final Object stage;
    public final Object[] event;
    public FMLBeforeStageEvent(Object stage, Object[] event){
        this.stage = stage;
        this.event = event;
    }
}
