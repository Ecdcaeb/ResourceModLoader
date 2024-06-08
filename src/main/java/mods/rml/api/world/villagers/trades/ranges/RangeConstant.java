package mods.rml.api.world.villagers.trades.ranges;

import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;

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
        public Factory() {
            super();
        }

        @Override
        public RangeBase get(JsonObject json) {
            return new RangeConstant(JsonUtils.getInt(json,"constant"));
        }
    }
}
