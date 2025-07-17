package rml.loader.api.event.early;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/9 21:33
 **/

import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.bus.EventBus;

@PublicAPI
public class RMLAfterInjectEvent{
    public static final EventBus<RMLAfterInjectEvent> BUS = EventBus.of();

}
