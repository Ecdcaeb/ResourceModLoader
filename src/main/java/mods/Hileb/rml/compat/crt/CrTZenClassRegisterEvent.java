package mods.Hileb.rml.compat.crt;

import crafttweaker.CraftTweakerAPI;
import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.RMLBus;

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
