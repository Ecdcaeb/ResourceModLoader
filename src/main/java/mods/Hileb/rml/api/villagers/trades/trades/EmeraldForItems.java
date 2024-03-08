package mods.Hileb.rml.api.villagers.trades.trades;

import com.google.gson.JsonObject;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:03
 **/
public class EmeraldForItems {
    public static class Loader extends TradeBase.Loader{
        public Loader() {
            super();
        }
        @Override
        public EntityVillager.ITradeList loadTrade(JsonObject trade) {
            JsonObject from= JsonUtils.getJsonObject(trade,"from");
            JsonObject to=JsonUtils.getJsonObject(trade,"to");
            if(to.has("item")){
                return new mods.Hileb.rml.api.villagers.trades.itrades.EmeraldForItems(TradeBase.getFullItemStack(JsonUtils.getJsonObject(to,"item")),
                        TradeBase.loadPrice(JsonUtils.getJsonObject(from,"price")
                        ));
            }
            else return new mods.Hileb.rml.api.villagers.trades.itrades.EmeraldForItems(TradeBase.getFullItemStack(JsonUtils.getJsonObject(from,"item")),
                    TradeBase.loadPrice(JsonUtils.getJsonObject(to,"price")
                    ));
        }
    }

}
