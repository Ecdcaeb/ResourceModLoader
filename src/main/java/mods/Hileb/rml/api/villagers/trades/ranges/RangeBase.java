package mods.Hileb.rml.api.villagers.trades.ranges;

import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Random;
import java.util.logging.Handler;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:25
 **/
public abstract class RangeBase{
    public RangeBase(){}
    public abstract int get(Random random);
}
