package mods.rml.api.world.values.items;

import com.google.gson.JsonObject;
import mods.rml.api.java.utils.RandomHolder;
import mods.rml.api.world.villagers.trades.ranges.RangeBase;
import mods.rml.api.world.villagers.trades.trades.TradeBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/3 18:25
 **/
public class RangeBaseItemStackFactory implements ItemFactory{
    public final RangeBase range;
    public final ItemStack baseItem;
    public RangeBaseItemStackFactory(RangeBase rangeBase, ItemStack baseItem){
        this.range = rangeBase;
        this.baseItem = baseItem;
    }

    @Override
    public ItemStack get() {
        ItemStack result = baseItem.copy();
        result.setCount(range.get(RandomHolder.RANDOM));
        return result;
    }

    public static RangeBaseItemStackFactory fromJson(JsonObject json){
        ItemStack stack = TradeBase.getFullItemStack(JsonUtils.getJsonObject(json,"item"));
        RangeBase rangeBase = TradeBase.loadPrice(JsonUtils.getJsonObject(json,"price"));
        return new RangeBaseItemStackFactory(rangeBase, stack);
    }
}
