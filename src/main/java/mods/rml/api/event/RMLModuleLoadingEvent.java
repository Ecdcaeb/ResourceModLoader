package mods.rml.api.event;

import mods.rml.api.mods.ContainerHolder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.HashSet;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/5 15:29
 **/
@Cancelable
public class RMLModuleLoadingEvent extends Event {
    public final HashSet<ContainerHolder> containerHolders;
    public final ContainerHolder.ModuleType module;
    public RMLModuleLoadingEvent(HashSet<ContainerHolder> containerHolders, ContainerHolder.ModuleType module){
        this.containerHolders = containerHolders;
        this.module = module;
    }

    public HashSet<ContainerHolder> getContainerHolders() {
        return containerHolders;
    }

    public ContainerHolder.ModuleType getModule() {
        return module;
    }

    public static HashSet<ContainerHolder> post(HashSet<ContainerHolder> containerHolders, ContainerHolder.ModuleType module){
        RMLModuleLoadingEvent event = new RMLModuleLoadingEvent(containerHolders, module);
        if (MinecraftForge.EVENT_BUS.post(event)) event.getContainerHolders().clear();
        return event.getContainerHolders();
    }


}
