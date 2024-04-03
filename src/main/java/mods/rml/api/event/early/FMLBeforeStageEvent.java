package mods.rml.api.event.early;

import mods.rml.api.EarlyClass;
import mods.rml.api.PublicAPI;

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
