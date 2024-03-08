package com.Hileb.custom_village_hileb.vanilla.trades;

import com.Hileb.custom_village_hileb.vanilla.trades.itrades.ListItemForEmeraldsHileb;
import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:26
 **/
public class ListItemForEmeralds {
    public static class Loader extends TradeBase.Loader{
        public Loader(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }
        @Override
        public EntityVillager.ITradeList loadTrade(JsonObject trade) {
            JsonObject from= JsonUtils.getJsonObject(trade,"from");
//            int minPrice=JsonUtils.getInt(from,"minPrice");
//            int maxPrice=JsonUtils.getInt(from,"maxPrice");
            JsonObject price=JsonUtils.getJsonObject(from,"price");
            JsonObject to=JsonUtils.getJsonObject(trade,"to");
            JsonObject item=JsonUtils.getJsonObject(to,"item");
            ItemStack stack=TradeBase.getFullItemStack(item);
            return new ListItemForEmeraldsHileb(stack,TradeBase.loadPrice(price));
        }
    }
}
