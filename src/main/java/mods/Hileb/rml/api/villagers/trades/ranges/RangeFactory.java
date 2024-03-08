package mods.Hileb.rml.api.villagers.trades.ranges;

import com.google.gson.JsonObject;
import mods.Hileb.rml.api.villagers.registry.CustomVillagerRegistry;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:29
 **/
public abstract class RangeFactory {
    public RangeFactory(){}
    public abstract RangeBase get(JsonObject json);
    public static RangeBase getRange(JsonObject json){
        String type="minecraft:price";
        if (json.has("type")){
            type= JsonUtils.getString(json,"type");
        }
        RangeFactory rangeFactory= CustomVillagerRegistry.RANGES.get(new ResourceLocation(type));
        if (rangeFactory==null){
            throw new RuntimeException("no range "+ type);
        }
        return rangeFactory.get(json);
    }
}
