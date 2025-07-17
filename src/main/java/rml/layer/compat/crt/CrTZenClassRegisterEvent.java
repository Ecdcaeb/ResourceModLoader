package rml.layer.compat.crt;

import crafttweaker.CraftTweakerAPI;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.bus.EventBus;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/15 17:30
 **/

@EarlyClass
@PublicAPI
public class CrTZenClassRegisterEvent {
    public static final EventBus<CrTZenClassRegisterEvent> BUS = EventBus.of();

    @PublicAPI
    public void register(Class<?> clazz){
        CraftTweakerAPI.registerClass(clazz);
    }

    public void register(Class<?>... clazz) {
        for (Class<?> cls : clazz) {
            register(cls);
        }
    }

    @PrivateAPI
    public static void post(){
        BUS.post(new CrTZenClassRegisterEvent());
    }

}
