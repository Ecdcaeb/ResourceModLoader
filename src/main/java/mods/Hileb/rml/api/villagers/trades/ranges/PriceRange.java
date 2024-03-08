package mods.Hileb.rml.api.villagers.trades.ranges;

import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:25
 **/
public class PriceRange extends RangeBase {
    EntityVillager.PriceInfo info;
    public PriceRange(EntityVillager.PriceInfo priceInfo){
        info=priceInfo;
    }

    @Override
    public int get(Random random) {
        return info.getPrice(random);
    }

    public static class Factory extends RangeFactory{
        public Factory() {
            super();
        }

        @Override
        public RangeBase get(JsonObject json) {
            int minPrice2= JsonUtils.getInt(json,"min");
            int maxPrice2=JsonUtils.getInt(json,"max");
            return new PriceRange( new EntityVillager.PriceInfo(minPrice2,maxPrice2));
        }
    }
}
