package mods.Hileb.rml;

import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.mods.BuffedModIDContainer;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:36
 **/
@PublicAPI
public class ResourceModLoader {
    @PublicAPI public static final String MODID="rml";
    @PublicAPI public static final String VERSION="1.0.10";
    @PrivateAPI private static final BuffedModIDContainer buffedModIDContainer=new BuffedModIDContainer();
    @PrivateAPI public static final HashSet<ContainerHolder> enabledModContainers=new HashSet<>();

    @PublicAPI public static ModContainer enableRML(ModContainer mod){
        enabledModContainers.add(new ContainerHolder(mod));
        return mod;
    }

    @PublicAPI public static ModContainer enableRML(ContainerHolder containerHolder){
        enabledModContainers.add(containerHolder);
        return containerHolder.container;
    }

    @PublicAPI public static ModContainer enableRML(ModContainer mod, ContainerHolder.Modules[] opinion){
        enabledModContainers.add(new ContainerHolder(mod, opinion));
        return mod;
    }

    @PublicAPI public static void enableRML(String modid){
        buffedModIDContainer.add(modid);
    }

    @PublicAPI public static void enableRML(String modid, ContainerHolder.Modules[] opinion){
        buffedModIDContainer.add(modid, opinion);
    }

    @PublicAPI public static void updateRMLContainerState(){
        enabledModContainers.addAll(buffedModIDContainer.getHolder());
        RMLFMLLoadingPlugin.Container.INSTANCE.getMetadata().childMods = enabledModContainers.stream().map(containerHolder -> containerHolder.container).collect(Collectors.toList());
    }

    @PublicAPI public static Set<ContainerHolder> getCurrentRMLContainerHolders(){
        updateRMLContainerState();
        return enabledModContainers;
    }

    @PublicAPI public static Set<ContainerHolder> getCurrentRMLContainerHolders(ContainerHolder.Modules module){
        return getCurrentRMLContainerHolders().stream().filter((containerHolder -> containerHolder.modules.contains(module))).collect(Collectors.toSet());
    }

}
