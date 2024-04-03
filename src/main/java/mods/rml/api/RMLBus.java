package mods.rml.api;

import com.google.common.eventbus.EventBus;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/9 21:34
 **/
@EarlyClass
@PublicAPI
public class RMLBus {
    @PublicAPI public static final EventBus BUS=new EventBus("rml");
}
