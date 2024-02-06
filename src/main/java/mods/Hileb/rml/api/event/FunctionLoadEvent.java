package mods.Hileb.rml.api.event;

import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:09
 **/
@PublicAPI
public class FunctionLoadEvent extends Event {
    @PrivateAPI public static void post(FunctionManager functionManager){
        MinecraftForge.EVENT_BUS.post(new FunctionLoadEvent(functionManager));
    }
    //field_193070_d - functions
    @PublicAPI public final FunctionManager functionManager;
    @PublicAPI public final Map<ResourceLocation, FunctionObject> functions;
    @PrivateAPI public FunctionLoadEvent(FunctionManager functionManager){
        functions= ReflectionHelper.getPrivateValue(FunctionManager.class,functionManager,"field_193070_d","functions");
        this.functionManager=functionManager;
    }
    @PublicAPI public void register(ResourceLocation name,FunctionObject function){
        functions.put(name,function);
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
