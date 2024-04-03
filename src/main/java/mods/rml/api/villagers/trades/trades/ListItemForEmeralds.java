package mods.rml.api.villagers.trades.trades;

import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:26
 **/
public class ListItemForEmeralds {
    public static class Loader extends TradeBase.Loader{
        public Loader() {
            super();
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
            return new mods.rml.api.villagers.trades.itrades.ListItemForEmeralds(stack,TradeBase.loadPrice(price));
        }
    }
}
