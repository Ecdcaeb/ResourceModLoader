package mods.rml.api.world.villagers.trades.itrades;

import mods.rml.api.world.villagers.trades.ranges.RangeBase;
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

public class EmeraldForItems implements EntityVillager.ITradeList
{
    public ItemStack buyingItem;
    public RangeBase price;

    public EmeraldForItems(ItemStack itemIn, RangeBase priceIn)
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
        ItemStack stack = buyingItem.copy();
        stack.setCount(i);

        recipeList.add(new MerchantRecipe(stack, Items.EMERALD));
    }
}
