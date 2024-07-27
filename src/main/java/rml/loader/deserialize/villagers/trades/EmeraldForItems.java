package rml.loader.deserialize.villagers.trades;

import com.google.gson.JsonObject;
import rml.jrx.announces.BeDiscovered;
import rml.jrx.utils.values.RandomIntSupplier;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;
import rml.deserializer.JsonDeserializeException;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 14:40
 * {@link EntityVillager.EmeraldForItems}
 **/

@BeDiscovered
public class EmeraldForItems implements EntityVillager.ITradeList
{
    public static final AbstractDeserializer<EntityVillager.ITradeList> DESERIALIZER = Deserializer.named(EntityVillager.ITradeList.class, new ResourceLocation("minecraft","emerald_for_items"))
            .require(JsonObject.class, "from")
            .require(JsonObject.class, "to")
            .decode((context -> {
                try {
                    if (context.get(JsonObject.class, "to").has("item")) {
                        return new EmeraldForItems(Deserializer.decode(ItemStack.class, JsonUtils.getJsonObject(context.get(JsonObject.class, "to"), "item")), Deserializer.decode(RandomIntSupplier.class, JsonUtils.getJsonObject(context.get(JsonObject.class, "from"), "price")));
                    } else
                        return new EmeraldForItems(Deserializer.decode(ItemStack.class, JsonUtils.getJsonObject(context.get(JsonObject.class, "from"), "item")), Deserializer.decode(RandomIntSupplier.class, JsonUtils.getJsonObject(context.get(JsonObject.class, "to"), "price")));
                }catch (Exception e){ throw new JsonDeserializeException(context.getJsonObject(), e); }
            })).build();
    public ItemStack buyingItem;
    public RandomIntSupplier price;

    public EmeraldForItems(ItemStack itemIn, RandomIntSupplier priceIn)
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
