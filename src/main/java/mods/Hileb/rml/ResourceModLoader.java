package mods.Hileb.rml;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:36
 **/
public class ResourceModLoader {
    public static final String MODID="rml";
    public static final String VERSION="1.0.3";
    private static final HashSet<String> buffedModContainerModid=new HashSet<>();
    public static final LinkedList<ModContainer> enabledModContainers=new LinkedList<>();
    public static ModContainer enableRML(ModContainer mod){
        enabledModContainers.add(mod);
        return mod;
    }
    public static void enableRML(String modid){
        buffedModContainerModid.add(modid);
    }
    public static void updateRMLContainerState(){
        Iterator<String> iterator=buffedModContainerModid.iterator();
        String modid;
        while (iterator.hasNext()){
            modid=iterator.next();
            if (Loader.isModLoaded(modid)){
                for(ModContainer container:Loader.instance().getActiveModList()){
                    if (modid.equals(container.getModId())){
                        enableRML(container);
                    }
                }
                iterator.remove();
            }
        }
    }
    public static List<ModContainer> getCurrentRMLContainers(){
        updateRMLContainerState();
        return enabledModContainers;
    }

}
