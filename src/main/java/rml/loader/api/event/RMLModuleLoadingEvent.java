package rml.loader.api.event;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Selection hook over a detached set. Listeners cannot mutate the pack registry. */
@PublicAPI
@Cancelable
public final class RMLModuleLoadingEvent extends Event {
    private final Set<ContainerHolder> containerHolders;
    private final ModuleType module;

    public RMLModuleLoadingEvent(Collection<ContainerHolder> containers, ModuleType module) {
        this.containerHolders = new LinkedHashSet<>(containers);
        this.module = module;
    }

    public Set<ContainerHolder> getContainerHolders() { return containerHolders; }
    public ModuleType getModule() { return module; }

    public static Set<ContainerHolder> post(Collection<ContainerHolder> containers, ModuleType module) {
        RMLModuleLoadingEvent event = new RMLModuleLoadingEvent(containers, module);
        if (MinecraftForge.EVENT_BUS.post(event)) event.containerHolders.clear();
        return event.containerHolders;
    }
}
