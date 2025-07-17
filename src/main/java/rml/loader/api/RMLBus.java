package rml.loader.api;

import com.google.common.eventbus.EventBus;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/9 21:34
 **/
@EarlyClass
@PublicAPI
public class RMLBus {
    @PublicAPI public static final EventBus BUS = new EventBus("rml");

    public static void register(String depCls, String handlerCls) {

    }
}
