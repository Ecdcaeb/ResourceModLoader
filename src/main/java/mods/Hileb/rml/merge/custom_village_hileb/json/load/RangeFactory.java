package com.Hileb.custom_village_hileb.json.load;

import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:29
 **/
public abstract class RangeFactory {
    public static HashMap<ResourceLocation,RangeFactory> FACTORY=new HashMap<>();
    public RangeFactory(ResourceLocation resourceLocation){
        FACTORY.put(resourceLocation,this);
    }
    public abstract RangeBase get(JsonObject json);
    public static RangeBase getRange(JsonObject json){
        String type="minecraft:price";
        if (json.has("type")){
            type= JsonUtils.getString(json,"type");
        }
        RangeFactory rangeFactory= FACTORY.get(new ResourceLocation(type));
        if (rangeFactory==null){
            throw new RuntimeException("no range "+ type);
        }
        return rangeFactory.get(json);
    }
}
