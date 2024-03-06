package mods.Hileb.rml.compat.fml;

import mods.Hileb.rml.api.RMLBus;
import mods.Hileb.rml.api.event.early.FMLBeforeStageEvent;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.LoaderState;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/10 0:13
 **/
public class RMLFMLHooks {
    @SuppressWarnings("unused")
    public static void beforeFMLBusEventSending(LoadController controller, LoaderState state, Object[] args){
        if (state.hasEvent()){
            RMLBus.BUS.post(new FMLBeforeStageEvent(state, args));
        }
    }
}
