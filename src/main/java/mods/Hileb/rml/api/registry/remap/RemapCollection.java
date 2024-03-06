package mods.Hileb.rml.api.registry.remap;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RemapCollection implements Iterable<Map.Entry<ResourceLocation,ResourceLocation>>{
    public final HashMap<ResourceLocation,ResourceLocation> remap = new HashMap<>();
    public final HashMap<ResourceLocation,ResourceLocation> demap = new HashMap<>();
    public final ResourceLocation registry;
    public RemapCollection(ResourceLocation registry){
        this.registry = registry;
    }



    @SuppressWarnings("rawtypes")
    @SubscribeEvent
    private void onEvent(RegistryEvent.MissingMappings event){//for the wide support.
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
        MinecraftForge.EVENT_BUS.unregister(this);
    }
    public void map(ResourceLocation oldMap, ResourceLocation newMap){
        if (demap.containsKey(oldMap)){
            ResourceLocation key = demap.get(oldMap);
            demap.put(newMap, key);
            remap.put(key, newMap);
        }else {
            demap.put(newMap, oldMap);
            remap.put(oldMap, newMap);
        }
    }

    @Override
    public Iterator<Map.Entry<ResourceLocation, ResourceLocation>> iterator() {
        return remap.entrySet().iterator();
    }

    public static class Manager{
        public static final HashMap<ResourceLocation,RemapCollection> GLOBAL_COLLECTION = new HashMap<>();

        public static RemapCollection create(ResourceLocation resourceLocation){
            RemapCollection remapCollection = new RemapCollection(resourceLocation);
            GLOBAL_COLLECTION.put(resourceLocation, remapCollection);
            MinecraftForge.EVENT_BUS.register(remapCollection);
            return remapCollection;
        }

        public static void merge(RemapCollection collection){
            RemapCollection remapCollection;
            if (!GLOBAL_COLLECTION.containsKey(collection.registry)){
                remapCollection = create(collection.registry);
            }else remapCollection = GLOBAL_COLLECTION.get(collection.registry);

            for(Map.Entry<ResourceLocation,ResourceLocation> entry:remapCollection){
                remapCollection.map(entry.getKey(), entry.getValue());
            }
        }
        private Manager(){}
    }

}
