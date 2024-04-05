package mods.rml.api.villagers.trades.ranges;

import com.google.gson.JsonObject;
import mods.rml.api.RMLRegistries;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:29
 **/
public abstract class RangeFactory extends IForgeRegistryEntry.Impl<RangeFactory> {
    public RangeFactory(){}
    public abstract RangeBase get(JsonObject json);
    public static RangeBase getRange(JsonObject json){
        String type = "minecraft:price";
        if (json.has("type")){
            type = JsonUtils.getString(json,"type");
        }
        RangeFactory rangeFactory = RMLRegistries.RANGE_FACTORIES.getValue(new ResourceLocation(type));
        if (rangeFactory == null){
            throw new RuntimeException("no range "+ type);
        }
        return rangeFactory.get(json);
    }
}
