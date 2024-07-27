package mods.rml.deserialize.villagers.trades;

import com.google.gson.JsonObject;
import rml.jrx.announces.BeDiscovered;
import rml.jrx.utils.values.RandomIntSupplier;
import mods.rml.api.world.villagers.TradeBase;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 14:20
 * {@link EntityVillager.ItemAndEmeraldToItem}
 **/
@BeDiscovered
public class ItemAndEmeraldToItem implements EntityVillager.ITradeList{

    public static final AbstractDeserializer<EntityVillager.ITradeList> DESERIALIZER = Deserializer.MANAGER.addEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft","list_item_for_emeralds"), EntityVillager.ITradeList.class,
            jsonElement -> {
                JsonObject trade = jsonElement.getAsJsonObject();
                JsonObject from = JsonUtils.getJsonObject(trade,"from");
                RandomIntSupplier p1 = TradeBase.loadPrice(JsonUtils.getJsonObject(from,"price"));
                RandomIntSupplier c1 = TradeBase.loadPrice(JsonUtils.getJsonObject(from,"count"));
                JsonObject to = JsonUtils.getJsonObject(trade,"to");
                RandomIntSupplier c2 = TradeBase.loadPrice(JsonUtils.getJsonObject(to,"count"));
                JsonObject item1 = JsonUtils.getJsonObject(from,"item");
                JsonObject item2 = JsonUtils.getJsonObject(to,"item");
                ItemAndEmeraldToItem t = new ItemAndEmeraldToItem();
                t.buyingPriceInfo = c1;
                t.buyingPriceInfo2 = p1;
                t.sellingPriceInfo = c2;
                t.buyingItemStack = TradeBase.loadItemStack(item1);
                t.sellingItemstack = TradeBase.loadItemStack(item2);

                return t;
            }));
    /**
     * The itemstack to buy with an emerald. The Item and damage value is used only, any tag data is not
     * retained.
     */
    public ItemStack buyingItemStack;
    /** The price info defining the amount of the buying item required with 1 emerald to match the selling item. */
    public RandomIntSupplier buyingPriceInfo;
    public RandomIntSupplier buyingPriceInfo2;
    /** The itemstack to sell. The item and damage value are used only, any tag data is not retained. */
    public ItemStack sellingItemstack;
    public RandomIntSupplier sellingPriceInfo;


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
