package mods.rml;

import mods.rml.api.PrivateAPI;
import mods.rml.api.PublicAPI;
import mods.rml.api.event.RMLModuleLoadingEvent;
import mods.rml.api.file.FileHelper;
import mods.rml.api.mods.BuffedModIDContainer;
import mods.rml.api.mods.ContainerHolder;
import mods.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:36
 **/
@PublicAPI
public class ResourceModLoader {
    @PublicAPI public static final String MODID="rml";
    @PublicAPI public static final String VERSION="1.0.11";
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

    @PublicAPI public static HashSet<ModContainer> getCurrentRMLContainers(){
        HashSet<ModContainer> containerHolders = new HashSet<>();
        for(ContainerHolder containerHolder : getCurrentRMLContainerHolders()){
            containerHolders.add(containerHolder.container);
        }
        return containerHolders;
    }

    @PublicAPI public static HashSet<ContainerHolder> getCurrentRMLContainerHolders(ContainerHolder.ModuleType module){
        HashSet<ContainerHolder> containerHolders = new HashSet<>();
        for(ContainerHolder containerHolder : getCurrentRMLContainerHolders()){
            if (containerHolder.hasModule(module)) containerHolders.add(containerHolder);
        }
        return containerHolders;
    }

    public static void loadModule(ContainerHolder.ModuleType module, ContainerHolder.ModuleConsumer consumer){
        Set<ContainerHolder> containerHolders = RMLModuleLoadingEvent.post(getCurrentRMLContainerHolders(module), module);
        for(ContainerHolder containerHolder : containerHolders){
            ModContainer oldActive = Loader.instance().activeModContainer();
            Loader.instance().setActiveModContainer(containerHolder.container);
            consumer.accept(module, containerHolder);
            Loader.instance().setActiveModContainer(oldActive);
        }
    }

    public static void loadModuleFindAssets(ContainerHolder.ModuleType module, FileHelper.ModFileConsumer consumer){
        Set<ContainerHolder> containerHolders = RMLModuleLoadingEvent.post(getCurrentRMLContainerHolders(module), module);
        for(ContainerHolder containerHolder : containerHolders){
            ModContainer oldActive = Loader.instance().activeModContainer();
            Loader.instance().setActiveModContainer(containerHolder.container);
            FileHelper.findAssets(containerHolder, containerHolder.modules.get(module), consumer);
            Loader.instance().setActiveModContainer(oldActive);
        }
    }

    public static void loadModuleFindAssets(ContainerHolder.ModuleType module,ContainerHolder.ModuleConsumer moduleConsumer, FileHelper.ModFileConsumer consumer){
        Set<ContainerHolder> containerHolders = RMLModuleLoadingEvent.post(getCurrentRMLContainerHolders(module), module);
        for(ContainerHolder containerHolder : containerHolders){
            ModContainer oldActive = Loader.instance().activeModContainer();
            Loader.instance().setActiveModContainer(containerHolder.container);
            moduleConsumer.accept(module, containerHolder);
            FileHelper.findAssets(containerHolder, containerHolder.modules.get(module), consumer);
            Loader.instance().setActiveModContainer(oldActive);
        }
    }


}
