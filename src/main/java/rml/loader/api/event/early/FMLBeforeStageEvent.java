package rml.loader.api.event.early;

import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.bus.DefaultEventBus;

@EarlyClass
@PublicAPI
public class FMLBeforeStageEvent {
    public static final DefaultEventBus<FMLBeforeStageEvent> BUS = DefaultEventBus.of();

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
