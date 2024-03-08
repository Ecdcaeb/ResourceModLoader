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
 * @Date 2023/8/16 14:40
 * {@link EntityVillager.EmeraldForItems}
 **/

public class EmeraldForItemsHileb implements EntityVillager.ITradeList
{
    public ItemStack buyingItem;
    public RangeBase price;

    public EmeraldForItemsHileb(ItemStack itemIn, RangeBase priceIn)
    {
        this.buyingItem = itemIn;
        this.price = priceIn;
    }
    public void addMerchantRecipe(IMerchant merchant, MerchantRecipeList recipeList, Random random)
    {
        int i = 1;

        if (this.price != null)
        {
            i = this.price.get(random);
        }
        ItemStack stack=buyingItem.copy();
        stack.setCount(i);

        recipeList.add(new MerchantRecipe(stack, Items.EMERALD));
    }
}
