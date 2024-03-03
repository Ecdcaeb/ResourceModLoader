package mods.Hileb.rml.api.registry.remap;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RemapCollection {
    public final HashMap<ResourceLocation,ResourceLocation> remap = new HashMap<>();
    public final ResourceLocation registry;
    public RemapCollection(ResourceLocation registry){
        this.registry = registry;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEvent(RegistryEvent.MissingMappings event){//for the wide support.
        if(event.getName().equals(this.registry)){
            Iterator<RegistryEvent.MissingMappings.Mapping> iterator = event.getAllMappings().iterator();
            RegistryEvent.MissingMappings.Mapping mapping;
            while (iterator.hasNext()){
                mapping = iterator.next();
                if (remap.containsKey(mapping.key)){
                    mapping.remap(mapping.registry.getValue(remap.get(mapping.key)));
                }
            }
        }
    }
    public void map(ResourceLocation oldMap, ResourceLocation newMap){
        ResourceLocation parent = null;
        for(Map.Entry<ResourceLocation,ResourceLocation> entry : remap.entrySet()){
            if (entry.getValue().equals(oldMap)) parent = entry.getKey();
        }
        if (parent != null) remap.put(parent, newMap);
        else remap.put(oldMap, newMap);
    }
}
