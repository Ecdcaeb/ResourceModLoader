package mods.Hileb.rml;

import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.api.mods.BuffedModIDContainer;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:36
 **/
@PublicAPI
public class ResourceModLoader {
    @PublicAPI public static final String MODID="rml";
    @PublicAPI public static final String VERSION="1.0.6";
    @PrivateAPI private static final BuffedModIDContainer buffedModIDContainer=new BuffedModIDContainer();
    @PrivateAPI public static final HashSet<ModContainer> enabledModContainers=new HashSet<>();
    @PublicAPI public static ModContainer enableRML(ModContainer mod){
        enabledModContainers.add(mod);
        return mod;
    }
    @PublicAPI public static void enableRML(String modid){
        buffedModIDContainer.add(modid);
    }
    @PublicAPI public static void updateRMLContainerState(){
        enabledModContainers.addAll(buffedModIDContainer.get());
        RMLFMLLoadingPlugin.Container.INSTANCE.getMetadata().childMods=new ArrayList<>(enabledModContainers);
    }
    @PublicAPI public static HashSet<ModContainer> getCurrentRMLContainers(){
        updateRMLContainerState();
        return enabledModContainers;
    }

}
