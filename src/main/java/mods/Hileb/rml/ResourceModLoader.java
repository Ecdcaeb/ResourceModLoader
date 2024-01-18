package mods.Hileb.rml;

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
public class ResourceModLoader {
    public static final String MODID="rml";
    public static final String VERSION="1.0.4";
    private static final BuffedModIDContainer buffedModIDContainer=new BuffedModIDContainer();
    public static final HashSet<ModContainer> enabledModContainers=new HashSet<>();
    public static ModContainer enableRML(ModContainer mod){
        enabledModContainers.add(mod);
        return mod;
    }
    public static void enableRML(String modid){
        buffedModIDContainer.add(modid);
    }
    public static void updateRMLContainerState(){
        enabledModContainers.addAll(buffedModIDContainer.get());
        RMLFMLLoadingPlugin.Container.INSTANCE.getMetadata().childMods=new ArrayList<>(enabledModContainers);
    }
    public static HashSet<ModContainer> getCurrentRMLContainers(){
        updateRMLContainerState();
        return enabledModContainers;
    }

}
