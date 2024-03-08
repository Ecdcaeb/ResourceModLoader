package com.Hileb.custom_village_hileb.json.load;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:23
 **/
public abstract class VillageReader {
    public static HashMap<ResourceLocation,VillageReader> REGISTERS=new HashMap<>();
    public VillageReader(ResourceLocation resourceLocation){
        REGISTERS.put(resourceLocation,this);
    }
    public abstract LoadedVillage load(JsonObject json);
}
