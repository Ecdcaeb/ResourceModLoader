package mods.rml.api.world.villagers.trades.ranges;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:25
 **/
public abstract class RangeBase{
    public RangeBase(){}
    public abstract int get(Random random);
}
