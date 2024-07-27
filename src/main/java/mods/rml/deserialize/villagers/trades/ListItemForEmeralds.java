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
 * @Date 2023/8/16 14:15
 * {@link EntityVillager.ListItemForEmeralds}
 **/

@BeDiscovered
public class ListItemForEmeralds implements EntityVillager.ITradeList {
    public static final AbstractDeserializer<EntityVillager.ITradeList> DESERIALIZER = Deserializer.MANAGER.addEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "item_and_emerald_to_item"), EntityVillager.ITradeList.class,
            jsonElement -> {
                JsonObject trade = jsonElement.getAsJsonObject();
                JsonObject from= JsonUtils.getJsonObject(trade,"from");
                JsonObject price = JsonUtils.getJsonObject(from,"price");
                JsonObject to = JsonUtils.getJsonObject(trade,"to");
                JsonObject item = JsonUtils.getJsonObject(to,"item");
                ItemStack stack = Deserializer.decode(ItemStack.class, item);
                return new ListItemForEmeralds(stack, TradeBase.loadPrice(price));
            }));
    /**
     * The item that is being bought for emeralds
     */
    public ItemStack itemToBuy;
    /**
     * The price info for the amount of emeralds to sell for, or if negative, the amount of the item to buy for
     * an emerald.
     */
    public RandomIntSupplier priceInfo;
    public ListItemForEmeralds(ItemStack stack, RandomIntSupplier priceInfo) {
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
