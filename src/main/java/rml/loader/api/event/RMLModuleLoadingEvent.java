package rml.loader.api.event;

import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import rml.loader.ResourceModLoader;

import java.util.HashSet;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/5 15:29
 *
 * Posted when a module is about to load.
 * {@link ResourceModLoader#loadModule(ModuleType, java.util.function.Consumer)}
 * {@link ResourceModLoader#loadModule(ModuleType, java.util.function.Consumer, Class)}
 * Cancel to skip every remaining container for that module.
 **/

@PublicAPI
@Cancelable
public class RMLModuleLoadingEvent extends Event {
    public final HashSet<ContainerHolder> containerHolders;
    public final ModuleType module;
    public RMLModuleLoadingEvent(HashSet<ContainerHolder> containerHolders, ModuleType module){
        this.containerHolders = containerHolders;
        this.module = module;
    }

    public HashSet<ContainerHolder> getContainerHolders() {
        return containerHolders;
    }

    public ModuleType getModule() {
        return module;
    }


    @PrivateAPI
    public static HashSet<ContainerHolder> post(HashSet<ContainerHolder> containerHolders, ModuleType module){
        RMLModuleLoadingEvent event = new RMLModuleLoadingEvent(containerHolders, module);
        if (MinecraftForge.EVENT_BUS.post(event)) event.getContainerHolders().clear();
        return event.getContainerHolders();
    }


}
