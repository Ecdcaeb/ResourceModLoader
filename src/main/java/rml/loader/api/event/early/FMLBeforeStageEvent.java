package rml.loader.api.event.early;

import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.bus.EventBus;

@EarlyClass
@PublicAPI
public class FMLBeforeStageEvent {
    public static final EventBus<FMLBeforeStageEvent> BUS = EventBus.of();

    public final Object stage;
    public final Object[] event;
    public FMLBeforeStageEvent(Object stage, Object[] event){
        this.stage = stage;
        this.event = event;
    }

    public static void post(Object stage, Object[] event) {
        BUS.post(new FMLBeforeStageEvent(stage, event));
    }
}
