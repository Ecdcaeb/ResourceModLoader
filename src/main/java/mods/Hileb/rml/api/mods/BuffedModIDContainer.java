package mods.Hileb.rml.api.mods;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/1/17 12:21
 **/
public class BuffedModIDContainer {
    public HashSet<String> modids=new HashSet<>();
    public void add(String modid){
        modids.add(modid);
    }
    public ArrayList<ModContainer> get(){
        ArrayList<ModContainer> containers=new ArrayList<>();
        for(ModContainer modContainer:Loader.instance().getModList()){
            if (modids.contains(modContainer.getModId())){
                containers.add(modContainer);
            }
        }
        return containers;
    }
    public void clear(){
        modids.clear();
    }
}
