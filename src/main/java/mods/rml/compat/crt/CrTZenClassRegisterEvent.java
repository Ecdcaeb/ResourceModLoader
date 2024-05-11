package mods.rml.compat.crt;

import crafttweaker.CraftTweakerAPI;
import mods.rml.api.announces.EarlyClass;
import mods.rml.api.announces.PrivateAPI;
import mods.rml.api.announces.PublicAPI;
import mods.rml.api.RMLBus;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/15 17:30
 **/

@EarlyClass
@PublicAPI
public class CrTZenClassRegisterEvent {
    @PublicAPI public void register(Class<?> clazz){
        CraftTweakerAPI.registerClass(clazz);
    }

    @PrivateAPI public static void post(){
        RMLBus.BUS.post(new CrTZenClassRegisterEvent());
    }

}
