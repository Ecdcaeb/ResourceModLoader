package com.Hileb.custom_village_hileb.vanilla.trades.ranges;

import com.Hileb.custom_village_hileb.json.load.RangeBase;
import com.Hileb.custom_village_hileb.json.load.RangeFactory;
import com.Hileb.custom_village_hileb.vanilla.trades.ranges.utils.PoissonDistribution;
import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 10:16
 **/
public class RangePoisson extends RangeBase {
    public final int min;
    public final int max;
    public final int lambda;
    public RangePoisson(int minIn,int maxIn,int lambdaIn){
        max=maxIn;
        min=minIn;
        lambda=lambdaIn;
    }

    @Override
    public int get(Random random) {
        return PoissonDistribution.getRandomlyInRange(random,lambda,min,max);
    }

    public static class Factory extends RangeFactory{
        public Factory(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }

        @Override
        public RangeBase get(JsonObject json) {
            int minPrice2= JsonUtils.getInt(json,"min");
            int maxPrice2=JsonUtils.getInt(json,"max");
            int lambda=JsonUtils.getInt(json,"lambda");
            return new RangePoisson(minPrice2,maxPrice2,lambda);
        }
    }
}
