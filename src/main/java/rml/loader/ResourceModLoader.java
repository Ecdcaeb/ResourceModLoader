package rml.loader;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.ReflectionUtil;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.event.RMLModuleLoadingEvent;
import rml.loader.api.mods.BuffedModIDContainer;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.utils.file.FileHelper;
import rml.loader.core.RMLFMLLoadingPlugin;

import javax.annotation.Nullable;
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
    @PublicAPI public static final int PACK_VERSION = 3;
    @PublicAPI public static final String MODID = "rml";
    @PublicAPI public static final String VERSION = "1.1.3";
    @PrivateAPI private static final BuffedModIDContainer buffedModIDContainer = new BuffedModIDContainer();
    @PrivateAPI public static final HashSet<ContainerHolder> enabledModContainers = new HashSet<>();

    @PublicAPI public static final Logger LOGGER = RMLFMLLoadingPlugin.LOGGER;

    @PublicAPI public static ModContainer enableRML(ModContainer mod){
        enabledModContainers.add(new ContainerHolder(mod));
        return mod;
    }

    @PublicAPI public static ModContainer enableRML(ContainerHolder containerHolder){
        enabledModContainers.add(containerHolder);
        return containerHolder.container;
    }

    @PublicAPI public static ModContainer enableRML(ModContainer mod, Module[] opinion){
        enabledModContainers.add(new ContainerHolder(mod, opinion));
        return mod;
    }

    @PublicAPI public static void enableRML(String modid){
        buffedModIDContainer.add(modid);
    }

    @PublicAPI public static void enableRML(String modid, Module[] opinion){
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

    @PublicAPI public static HashSet<ContainerHolder> getCurrentRMLContainerHolders(ModuleType module){
        HashSet<ContainerHolder> containerHolders = new HashSet<>();
        for(ContainerHolder containerHolder : getCurrentRMLContainerHolders()){
            if (containerHolder.hasModule(module)) containerHolders.add(containerHolder);
        }
        return containerHolders;
    }

    public static void loadContainerHolders(ModuleType module, Consumer<ContainerHolder> consumer){
        Set<ContainerHolder> containerHolders = RMLModuleLoadingEvent.post(getCurrentRMLContainerHolders(module), module);
        for(ContainerHolder containerHolder : containerHolders){
            consumer.accept(containerHolder);
        }
    }

    public static void loadModule(ModuleType module, Consumer<ContainerHolder.ModuleConsumeContext> consumer, Class<?> caller){
        Set<ContainerHolder> containerHolders = RMLModuleLoadingEvent.post(getCurrentRMLContainerHolders(module), module);
        for(ContainerHolder containerHolder : containerHolders){
            ModContainer oldActive = Loader.instance().activeModContainer();
            Loader.instance().setActiveModContainer(containerHolder.getContainer());
            FileHelper.findAssets(LogManager.getLogger(caller), containerHolder, containerHolder.getModules().get(module), consumer);
            Loader.instance().setActiveModContainer(oldActive);
        }
    }

    public static void loadModule(ModuleType module, Consumer<ContainerHolder.ModuleConsumeContext> consumer){
        loadModule(module, consumer, ReflectionUtil.getCallerClass(2));
    }

    @Nullable
    public static ContainerHolder of(ModContainer modContainer){
        updateRMLContainerState();
        for(ContainerHolder containerHolder : enabledModContainers){
            if (containerHolder.getContainer().getModId().equals(modContainer.getModId())) return containerHolder;
        }
        return null;
    }
}
