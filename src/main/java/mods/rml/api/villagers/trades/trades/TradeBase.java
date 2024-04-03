package mods.rml.api.villagers.trades.trades;

import com.google.gson.*;
import mods.rml.api.java.reflection.ReflectionHelper;
import mods.rml.api.villagers.LoadedVillage;
import mods.rml.api.villagers.VillageReader;
import mods.rml.api.villagers.trades.ranges.RangeBase;
import mods.rml.api.villagers.trades.ranges.RangeFactory;
import mods.rml.deserialize.RMLDeserializeLoader;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:44
 **/
@SuppressWarnings("all")
public class TradeBase {
    public static class Loaded extends LoadedVillage {
        public ResourceLocation profession;
        public String career;
        public EntityVillager.ITradeList list;
        public int level;

        public Loaded(ResourceLocation professionIn, String careerIn, int levelIn,EntityVillager.ITradeList listIn){
            profession=professionIn;
            list=listIn;
            level=levelIn;
            career=careerIn;
        }
        @Override
        public void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r) {
            VillagerRegistry.VillagerProfession profession_v = r.getValue(this.profession);

            for(VillagerRegistry.VillagerCareer career1 : (List<VillagerRegistry.VillagerCareer>) ReflectionHelper.getPrivateValue(VillagerRegistry.VillagerProfession.class, profession_v, "careers")){
                if (career1.getName().equals(career)){
                    career1.addTrade(level,this.list);
                }
            }
        }
    }
    public abstract static class Loader extends VillageReader {
        public Loader() {
            super();
        }
        @Override
        public LoadedVillage load(JsonObject json) {
            int level=1;
            if (json.has("level"))level= JsonUtils.getInt(json,"level");
            String pro=JsonUtils.getString(json,"profession");
            String car=JsonUtils.getString(json,"career");
            JsonObject trade=JsonUtils.getJsonObject(json,"trade");
            EntityVillager.ITradeList iTradeList=loadTrade(trade);
            return new Loaded(new ResourceLocation(pro),car,level,iTradeList);
        }

        public abstract EntityVillager.ITradeList loadTrade(JsonObject trade);
    }
    public static ItemStack getItemStack(JsonObject json)
    {
        String itemName = JsonUtils.getString(json, "item");

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));

        if (item == null)
            throw new JsonSyntaxException("Unknown item '" + itemName + "'");

        if (item.getHasSubtypes() && !json.has("data"))
            throw new JsonParseException("Missing data for item '" + itemName + "'");

        if (json.has("nbt"))
        {
            // Lets hope this works? Needs test
            try
            {
                JsonElement element = json.get("nbt");
                NBTTagCompound nbt;
                if(element.isJsonObject())
                    nbt = JsonToNBT.getTagFromJson(RMLDeserializeLoader.CustomVillageLoader.GSON.toJson(element));
                else
                    nbt = JsonToNBT.getTagFromJson(element.getAsString());

                NBTTagCompound tmp = new NBTTagCompound();
                if (nbt.hasKey("ForgeCaps"))
                {
                    tmp.setTag("ForgeCaps", nbt.getTag("ForgeCaps"));
                    nbt.removeTag("ForgeCaps");
                }

                tmp.setTag("tag", nbt);
                tmp.setString("id", itemName);
                tmp.setInteger("Count", JsonUtils.getInt(json, "count", 1));
                tmp.setInteger("Damage", JsonUtils.getInt(json, "data", 0));

                return new ItemStack(tmp);
            }
            catch (NBTException e)
            {
                throw new JsonSyntaxException("Invalid NBT Entry: " + e.toString());
            }
        }

        return new ItemStack(item, JsonUtils.getInt(json, "count", 1), JsonUtils.getInt(json, "data", 0));
    }
    public static List<EnchantmentData> loadEnch(JsonObject jsonObject){
        List<EnchantmentData> enchantmentData=new ArrayList<>();
        JsonArray jsonObjects=JsonUtils.getJsonArray(jsonObject,"enchantment");
        for(JsonElement element:jsonObjects){
            if (element instanceof JsonObject){
                JsonObject object=(JsonObject) element;
                int level=1;
                if (object.has("level")){
                    JsonElement p1=object.get("level");
                    if (p1.isJsonPrimitive() &&p1.getAsJsonPrimitive().isNumber()){
                        level=p1.getAsInt();
                    }else if (p1.isJsonObject()){
                        RangeBase rangeBase=TradeBase.loadPrice(JsonUtils.getJsonObject(object,"level"));
                        level=rangeBase.get(new Random());
                    }
                }
                FMLLog.log.error("level : "+level);

                ResourceLocation resourceLocation=new ResourceLocation(JsonUtils.getString(object,"name"));
                enchantmentData.add(new EnchantmentData(ForgeRegistries.ENCHANTMENTS.getValue(resourceLocation),level));
            }
        }
        return enchantmentData;
    }
    public static ItemStack getFullItemStack(JsonObject jsonObject){
        ItemStack stack=TradeBase.getItemStack(jsonObject);
        if (jsonObject.has("enchantment")){
            for(EnchantmentData data:TradeBase.loadEnch(jsonObject)){
                stack.addEnchantment(data.enchantment,data.enchantmentLevel);
            }
        }
        return stack;
    }
    public static RangeBase loadPrice(JsonObject jsonObject){
        return RangeFactory.getRange(jsonObject);
    }
}
