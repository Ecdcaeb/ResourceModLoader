package mods.Hileb.rml.api.event.early;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/6 23:14
 **/
public class FMLBeforeStageEvent {
    public final Object stage;
    public final Object[] event;
    public FMLBeforeStageEvent(Object stage, Object[] event){
        this.stage = stage;
        this.event = event;
    }
}
