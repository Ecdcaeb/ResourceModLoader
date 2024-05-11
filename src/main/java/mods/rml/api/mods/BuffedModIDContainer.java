package mods.rml.api.mods;

import mods.rml.api.announces.PublicAPI;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/1/17 12:21
 **/
@Deprecated
@PublicAPI
public class BuffedModIDContainer {
    @PublicAPI public HashSet<String> modids = new HashSet<>();
    @PublicAPI public HashMap<String, ContainerHolder.Module[]> opinions = new HashMap<>();

    @PublicAPI public void add(String modid){
        this.add(modid, ContainerHolder.ModuleType.getAllForDefault());
    }

    @PublicAPI public void add(String modid, ContainerHolder.Module[] opinion){
        modids.add(modid);
        opinions.put(modid, opinion);
    }

    @PublicAPI public ArrayList<ContainerHolder> getHolder(){
        ArrayList<ContainerHolder> containers=new ArrayList<>();
        for(ModContainer modContainer:Loader.instance().getModList()){
            if (modids.contains(modContainer.getModId())){
                containers.add(new ContainerHolder(modContainer, opinions.get(modContainer.getModId())));
            }
        }
        return containers;
    }

    @PublicAPI public void clear(){
        modids.clear();
        opinions.clear();
    }
}
