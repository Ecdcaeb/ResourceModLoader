package mods.rml.api.world.villagers.trades.itrades;

import mods.rml.api.java.utils.values.RandomInt;
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
 * @Date 2023/8/16 14:20
 * {@link EntityVillager.ItemAndEmeraldToItem}
 **/
public class ItemAndEmeraldToItem implements EntityVillager.ITradeList{
    /**
     * The itemstack to buy with an emerald. The Item and damage value is used only, any tag data is not
     * retained.
     */
    public ItemStack buyingItemStack;
    /** The price info defining the amount of the buying item required with 1 emerald to match the selling item. */
    public RandomInt buyingPriceInfo;
    public RandomInt buyingPriceInfo2;
    /** The itemstack to sell. The item and damage value are used only, any tag data is not retained. */
    public ItemStack sellingItemstack;
    public RandomInt sellingPriceInfo;


    public ItemAndEmeraldToItem(){}
    public void addMerchantRecipe(IMerchant merchant, MerchantRecipeList recipeList, Random random)
    {
        int i = this.buyingPriceInfo.get(random);
        int j = this.sellingPriceInfo.get(random);
        int k = this.buyingPriceInfo.get(random);
        ItemStack stack1=this.buyingItemStack.copy();
        stack1.setCount(i);
        ItemStack stack2=this.sellingItemstack.copy();
        ItemStack stack=new ItemStack(Items.EMERALD);
        stack.setCount(k);
        recipeList.add(new MerchantRecipe(stack1, stack, stack2));
    }

}
