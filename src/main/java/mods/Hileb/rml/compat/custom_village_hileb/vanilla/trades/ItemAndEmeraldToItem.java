package com.Hileb.custom_village_hileb.vanilla.trades;

import com.Hileb.custom_village_hileb.json.load.RangeBase;
import com.Hileb.custom_village_hileb.vanilla.trades.itrades.ItemAndEmeraldToItemHileb;
import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:30
 **/
public class ItemAndEmeraldToItem {
    public static class Loader extends TradeBase.Loader{
        public Loader(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }
        @Override
        public EntityVillager.ITradeList loadTrade(JsonObject trade) {
            JsonObject from= JsonUtils.getJsonObject(trade,"from");
            RangeBase p1=TradeBase.loadPrice(JsonUtils.getJsonObject(from,"price"));
            RangeBase c1=TradeBase.loadPrice(JsonUtils.getJsonObject(from,"count"));
            JsonObject to=JsonUtils.getJsonObject(trade,"to");
            RangeBase c2=TradeBase.loadPrice(JsonUtils.getJsonObject(to,"count"));
            JsonObject item1=JsonUtils.getJsonObject(from,"item");
            JsonObject item2=JsonUtils.getJsonObject(to,"item");
            ItemAndEmeraldToItemHileb t=new ItemAndEmeraldToItemHileb();
            t.buyingPriceInfo=c1;
            t.buyingPriceInfo2=p1;
            t.sellingPriceInfo=c2;
            t.buyingItemStack=TradeBase.getFullItemStack(item1);
            t.sellingItemstack=TradeBase.getFullItemStack(item2);

            return t;
        }
    }
}
