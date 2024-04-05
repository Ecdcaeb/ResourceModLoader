package mods.rml.api.event;

import mods.rml.api.mods.ContainerHolder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.HashSet;
import java.util.Set;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/5 15:29
 **/
@Cancelable
public class RMLModuleLoadingEvent extends Event {
    public final Set<ContainerHolder> containerHolders;
    public final ContainerHolder.Modules module;
    public RMLModuleLoadingEvent(Set<ContainerHolder> containerHolders, ContainerHolder.Modules module){
        this.containerHolders = containerHolders;
        this.module = module;
    }

    public Set<ContainerHolder> getContainerHolders() {
        return containerHolders;
    }

    public ContainerHolder.Modules getModule() {
        return module;
    }

    public static Set<ContainerHolder> post(Set<ContainerHolder> containerHolders, ContainerHolder.Modules module){
        RMLModuleLoadingEvent event = new RMLModuleLoadingEvent(containerHolders, module);
        if (MinecraftForge.EVENT_BUS.post(event)) event.getContainerHolders().clear();
        return event.getContainerHolders();
    }


}
