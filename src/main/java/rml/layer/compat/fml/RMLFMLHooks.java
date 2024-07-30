package rml.layer.compat.fml;

import rml.loader.api.RMLBus;
import rml.loader.api.event.early.FMLBeforeStageEvent;
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
