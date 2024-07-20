package mods.rml.api.world.villagers.trades.trades;

import mods.rml.api.java.utils.values.ranges.RangeBase;
import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:30
 **/
public class ItemAndEmeraldToItem {
    public static class Loader extends TradeBase.Loader{
        public Loader() {
            super();
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
            mods.rml.api.world.villagers.trades.itrades.ItemAndEmeraldToItem t=new mods.rml.api.world.villagers.trades.itrades.ItemAndEmeraldToItem();
            t.buyingPriceInfo=c1;
            t.buyingPriceInfo2=p1;
            t.sellingPriceInfo=c2;
            t.buyingItemStack=TradeBase.getFullItemStack(item1);
            t.sellingItemstack=TradeBase.getFullItemStack(item2);

            return t;
        }
    }
}
