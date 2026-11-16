package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.WorldServer;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntProvider;
import rml.loader.api.world.villagers.IVillager;
import rml.loader.api.world.villagers.TradeBase;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import java.util.ArrayList;
import java.util.List;

/** Trade sets compose existing profession/career installers and native offer factories. */
final class GameplayTradeDecoders {
    private GameplayTradeDecoders() { }
    static void register(DeserializerManager manager) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "trade_set"), IVillager.class, value -> {
            JsonObject json = value.getAsJsonObject();
            GameplayFields.only(json, "entries", "profession", "career", "level", "trades");
            if (json.has("entries")) {
                if (json.has("profession") || json.has("career") || json.has("trades") || json.has("level")) throw new IllegalArgumentException("Composed trade set cannot also declare a profession/career/level");
                IVillager[] children = manager.decode(IVillager[].class, json.get("entries"));
                return registry -> { for (IVillager child : children) child.run(registry); };
            }
            ResourceLocation profession = manager.decode(ResourceLocation.class, json.get("profession"));
            String career = manager.decode(String.class, json.get("career"));
            int level = json.has("level") ? manager.decode(Integer.class, json.get("level")) : 1;
            if (level < 1 || career.isEmpty()) throw new IllegalArgumentException("Trade set requires a career and positive level");
            EntityVillager.ITradeList[] trades = manager.decode(EntityVillager.ITradeList[].class, json.get("trades"));
            return registry -> {
                if (!registry.containsKey(profession)) throw new IllegalArgumentException("Unknown villager profession: " + profession);
                net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession nativeProfession = registry.getValue(profession);
                List<?> careers = rml.loader.api.reflection.jvm.ReflectionHelper.getPrivateValue(net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession.class, nativeProfession, "careers");
                for (Object candidate : careers) {
                    net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer nativeCareer = (net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer) candidate;
                    if (nativeCareer.getName().equals(career)) { nativeCareer.addTrade(level, trades); return; }
                }
                throw new IllegalArgumentException("Unknown villager career " + profession + ":" + career);
            };
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "offer"), EntityVillager.ITradeList.class, value -> {
            JsonObject json = value.getAsJsonObject();
            GameplayFields.only(json, "buy", "sell", "buy_secondary", "max_uses", "buy_modifier", "sell_modifier", "conditions", "reward_experience");
            ItemStack buy = GameplayStackDecoder.decode(manager, json.get("buy"));
            ItemStack sell = GameplayStackDecoder.decode(manager, json.get("sell"));
            ItemStack second = json.has("buy_secondary") ? GameplayStackDecoder.decode(manager, json.get("buy_secondary")) : ItemStack.EMPTY;
            if (buy.isEmpty() || sell.isEmpty()) throw new IllegalArgumentException("Trade buy and sell stacks must not be empty");
            IntProvider uses = integer(manager, json, "max_uses", 7);
            if (uses.getMinValue() < 1) throw new IllegalArgumentException("max_uses must be positive");
            DataRules.Modifier buyModifier = json.has("buy_modifier") ? manager.decode(DataRules.Modifier.class, json.get("buy_modifier")) : (stack, context) -> stack;
            DataRules.Modifier sellModifier = json.has("sell_modifier") ? manager.decode(DataRules.Modifier.class, json.get("sell_modifier")) : (stack, context) -> stack;
            DataRules.Test condition = json.has("conditions") ? manager.decode(DataRules.Test.class, json.get("conditions")) : context -> true;
            boolean rewardXp = !json.has("reward_experience") || json.get("reward_experience").getAsBoolean();
            return (merchant, recipes, random) -> {
                Entity entity = merchant instanceof Entity ? (Entity) merchant : null;
                DataContext context = new DataContext(entity != null && entity.world instanceof WorldServer ? (WorldServer) entity.world : null, entity, merchant.getPos(), random, 0);
                if (!condition.test(context)) return;
                ItemStack input = buyModifier.apply(buy.copy(), context), output = sellModifier.apply(sell.copy(), context);
                if (input.isEmpty() || output.isEmpty()) throw new IllegalStateException("Trade modifier produced an empty buy/sell stack");
                MerchantRecipe offer = new MerchantRecipe(input, second.copy(), output, 0, uses.sample(random));
                // MerchantRecipe stores rewardExp internally; deserialize the complete native offer to install that flag.
                net.minecraft.nbt.NBTTagCompound tag = offer.writeToTags();
                tag.setBoolean("rewardExp", rewardXp);
                recipes.add(new MerchantRecipe(tag));
            };
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "trade_pool"), EntityVillager.ITradeList.class, value -> {
            JsonObject json = value.getAsJsonObject();
            GameplayFields.only(json, "count", "entries");
            IntProvider count = integer(manager, json, "count", 1);
            if (count.getMinValue() < 0) throw new IllegalArgumentException("Trade pool count must be nonnegative");
            List<EntityVillager.ITradeList> children = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            long total = 0;
            for (JsonElement entry : json.getAsJsonArray("entries")) {
                JsonObject child = entry.getAsJsonObject();
                GameplayFields.only(child, "trade", "weight");
                int weight = child.has("weight") ? manager.decode(Integer.class, child.get("weight")) : 1;
                if (weight <= 0 || (total += weight) > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid trade pool weight");
                children.add(manager.decode(EntityVillager.ITradeList.class, child.get("trade")));
                weights.add(weight);
            }
            return (merchant, recipes, random) -> {
                List<Integer> remaining = new ArrayList<>();
                int weight = 0;
                for (int i = 0; i < children.size(); i++) { remaining.add(i); weight += weights.get(i); }
                int draws = Math.min(children.size(), count.sample(random));
                for (int draw = 0; draw < draws; draw++) {
                    int chosen = random.nextInt(weight), position = 0;
                    while (position < remaining.size() - 1 && (chosen -= weights.get(remaining.get(position))) >= 0) position++;
                    int index = remaining.remove(position);
                    weight -= weights.get(index);
                    children.get(index).addMerchantRecipe(merchant, recipes, random);
                }
            };
        }));
    }
    private static IntProvider integer(DeserializerManager manager, JsonObject json, String key, int fallback) throws JsonDeserializeException {
        return json.has(key) ? manager.decode(IntProvider.class, json.get(key)) : new IntProvider(fallback, fallback, random -> fallback);
    }
}
