package com.Hileb.custom_village_hileb.vanilla.trades.ranges;

import com.Hileb.custom_village_hileb.json.load.RangeBase;
import com.Hileb.custom_village_hileb.json.load.RangeFactory;
import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:28
 **/
public class RangeConstant extends RangeBase {
    public static final RangeConstant ONE=new RangeConstant(1);
    final int a;
    public RangeConstant(int aIn){
        a=aIn;
    }

    @Override
    public int get(Random random) {
        return a;
    }

    public static class Factory extends RangeFactory {
        public Factory(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }

        @Override
        public RangeBase get(JsonObject json) {
            return new RangeConstant(JsonUtils.getInt(json,"constant"));
        }
    }
}
