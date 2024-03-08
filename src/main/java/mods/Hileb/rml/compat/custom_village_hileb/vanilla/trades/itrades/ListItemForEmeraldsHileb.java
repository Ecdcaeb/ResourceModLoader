package com.Hileb.custom_village_hileb.vanilla.trades.itrades;

import com.Hileb.custom_village_hileb.json.load.RangeBase;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 14:15
 * {@link EntityVillager.ListItemForEmeralds}
 **/
public class ListItemForEmeraldsHileb implements EntityVillager.ITradeList {
    /**
     * The item that is being bought for emeralds
     */
    public ItemStack itemToBuy;
    /**
     * The price info for the amount of emeralds to sell for, or if negative, the amount of the item to buy for
     * an emerald.
     */
    public RangeBase priceInfo;
    public ListItemForEmeraldsHileb(ItemStack stack, RangeBase priceInfo) {
        this.itemToBuy = stack;
        this.priceInfo = priceInfo;
    }

    public void addMerchantRecipe(IMerchant merchant, MerchantRecipeList recipeList, Random random) {
        int i = 1;

        if (this.priceInfo != null) {
            i = this.priceInfo.get(random);
        }

        ItemStack stackEmerald=new ItemStack(Items.EMERALD);
        ItemStack stack=itemToBuy.copy();

        if (i < 0) {
            stackEmerald.setCount(1);
            stack.setCount(-i);
        } else {
            stackEmerald.setCount(i);
            stack.setCount(1);
        }
        recipeList.add(new MerchantRecipe(stackEmerald, stack));
    }
}
