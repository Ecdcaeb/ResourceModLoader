package mods.rml.api.world.villagers.trades.itrades;

import mods.rml.api.world.villagers.trades.ranges.RangeBase;
import mods.rml.api.world.villagers.trades.trades.TradeBase;
import mods.rml.api.world.villagers.trades.ranges.RangeConstant;
import com.google.gson.JsonObject;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 10:27
 **/
public class SlotRecipe implements EntityVillager.ITradeList {
    public RangeBase range1;
    public RangeBase range2;
    public RangeBase range3;

    public ItemStack stack1;
    public ItemStack stack2;
    public ItemStack stack3;
    @Override
    public void addMerchantRecipe(IMerchant merchant, MerchantRecipeList recipeList, Random random) {
        ItemStack stack_1=stack1.copy();
        ItemStack stack_2=stack2.copy();
        ItemStack stack_3=stack3.copy();
        stack_1.setCount(range1.get(random));
        stack_2.setCount(range2.get(random));
        stack_3.setCount(range3.get(random));

        recipeList.add(new MerchantRecipe(stack_1, stack_2, stack_3));
    }
    public static class Loader extends TradeBase.Loader {
        public Loader() {
            super();
        }

        @Override
        public EntityVillager.ITradeList loadTrade(JsonObject trade) {
            ItemStack s1=ItemStack.EMPTY;
            ItemStack s2=ItemStack.EMPTY;
            ItemStack s3=ItemStack.EMPTY;
            RangeBase r1= RangeConstant.ONE;
            RangeBase r2= RangeConstant.ONE;
            RangeBase r3= RangeConstant.ONE;

            JsonObject from= JsonUtils.getJsonObject(trade,"from");
            JsonObject to=JsonUtils.getJsonObject(trade,"to");
            if (from.has("slot1")){
                JsonObject obj=JsonUtils.getJsonObject(from,"slot1");
                s1=TradeBase.getFullItemStack(JsonUtils.getJsonObject(obj,"item"));
                r1=TradeBase.loadPrice(JsonUtils.getJsonObject(obj,"price"));
            }
            if (from.has("slot2")){
                JsonObject obj=JsonUtils.getJsonObject(from,"slot2");
                s2=TradeBase.getFullItemStack(JsonUtils.getJsonObject(obj,"item"));
                r2=TradeBase.loadPrice(JsonUtils.getJsonObject(obj,"price"));
            }
            if (to.has("slot3")){
                JsonObject obj=JsonUtils.getJsonObject(to,"slot3");
                s3=TradeBase.getFullItemStack(JsonUtils.getJsonObject(obj,"item"));
                r3=TradeBase.loadPrice(JsonUtils.getJsonObject(obj,"price"));
            }
            SlotRecipe recipe=new SlotRecipe();
            recipe.range1=r1;
            recipe.range2=r2;
            recipe.range3=r3;

            recipe.stack1=s1;
            recipe.stack2=s2;
            recipe.stack3=s3;

            return recipe;
        }
    }
}
