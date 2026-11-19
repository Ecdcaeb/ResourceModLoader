package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntRange;
import rml.deserializer.value.WeightedList;
import rml.loader.runtime.function.FunctionDataPath;
import rml.loader.runtime.function.FunctionStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Composed item transformations; every nested value uses the caller's decoder registry. */
final class ContextModifierDecoders {
    private ContextModifierDecoders() { }

    static void register(DeserializerManager manager) {
        for (String name : new String[]{"conditional", "weighted", "limit_count", "set_enchantments", "apply_bonus", "set_name", "set_lore", "copy_nbt",
                "copy_name", "fill_player_head", "set_contents", "set_loot_table"}) {
            entry(manager, "rml", name);
        }
        for (String name : new String[]{"furnace_smelt", "enchant_randomly", "enchant_with_levels", "looting_enchant"}) {
            entry(manager, "minecraft", name);
            entry(manager, "rml", name);
        }
    }

    private static void entry(DeserializerManager manager, String domain, String name) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(domain, name), DataRules.Modifier.class,
                json -> decode(manager, json.getAsJsonObject())));
    }

    private static DataRules.Modifier decode(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        DataRules.Test[] conditions = object.has("conditions") ? manager.decode(DataRules.Test[].class, object.get("conditions")) : new DataRules.Test[0];
        DataRules.Modifier operation = operation(manager, object);
        if (conditions.length == 0) return operation;
        return (stack, context) -> {
            for (DataRules.Test condition : conditions) if (!condition.test(context)) return stack;
            return operation.apply(stack, context);
        };
    }

    private static DataRules.Modifier operation(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        String type = new ResourceLocation(object.get("type").getAsString()).getPath();
        switch (type) {
            case "conditional": {
                fields(object, "condition", "on_true", "on_false");
                DataRules.Test test = manager.decode(DataRules.Test.class, required(object, "condition"));
                DataRules.Modifier yes = manager.decode(DataRules.Modifier.class, required(object, "on_true"));
                DataRules.Modifier no = object.has("on_false") ? manager.decode(DataRules.Modifier.class, object.get("on_false")) : null;
                return (stack, context) -> { DataRules.Modifier selected = test.test(context) ? yes : no; return selected == null ? stack : selected.apply(stack, context); };
            }
            case "weighted": {
                fields(object, "distribution");
                WeightedList<DataRules.Modifier> distribution = manager.decode(new TypeToken<WeightedList<DataRules.Modifier>>() { }.getType(), required(object, "distribution"));
                return (stack, context) -> distribution.sample(context.getRandom()).apply(stack, context);
            }
            case "limit_count": {
                fields(object, "limit");
                IntRange limit = manager.decode(IntRange.class, required(object, "limit"));
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    int count = Math.max(0, Math.max(limit.getMin(), Math.min(limit.getMax(), stack.getCount())));
                    if (count == stack.getCount()) return stack;
                    ItemStack copy = stack.copy(); copy.setCount(count); return copy;
                };
            }
            case "set_enchantments": {
                fields(object, "enchantments", "add");
                Map<ResourceLocation, DataRules.NumberValue> definitions = manager.decode(new TypeToken<Map<ResourceLocation, DataRules.NumberValue>>() { }.getType(), required(object, "enchantments"));
                Map<Enchantment, DataRules.NumberValue> values = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, DataRules.NumberValue> entry : definitions.entrySet()) {
                    Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(entry.getKey());
                    if (enchantment == null) throw error(object, "Unknown enchantment " + entry.getKey());
                    values.put(enchantment, entry.getValue());
                }
                boolean add = object.has("add") && manager.decode(Boolean.class, object.get("add"));
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
                    for (Map.Entry<Enchantment, DataRules.NumberValue> entry : values.entrySet()) {
                        double value = entry.getValue().evaluate(context) + (add ? enchantments.getOrDefault(entry.getKey(), 0) : 0);
                        if (!Double.isFinite(value) || value < 0 || value > Short.MAX_VALUE) throw new IllegalArgumentException("Enchantment level must be within 0..32767");
                        int level = (int) Math.floor(value);
                        if (level == 0) enchantments.remove(entry.getKey()); else enchantments.put(entry.getKey(), level);
                    }
                    ItemStack copy = stack.copy();
                    // Native enchanted-book writes merge levels; clear the stored list before replacing it.
                    if (copy.getItem() == net.minecraft.init.Items.ENCHANTED_BOOK && copy.hasTagCompound()) copy.getTagCompound().removeTag("StoredEnchantments");
                    EnchantmentHelper.setEnchantments(enchantments, copy);
                    return copy;
                };
            }
            case "furnace_smelt": {
                fields(object);
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ItemStack smelted = FurnaceRecipes.instance().getSmeltingResult(stack);
                    if (smelted.isEmpty()) return stack;
                    ItemStack copy = smelted.copy();
                    copy.setCount(stack.getCount());
                    return copy;
                };
            }
            case "enchant_randomly": {
                fields(object, "enchantments");
                List<ResourceLocation> allowed = object.has("enchantments")
                        ? java.util.Arrays.asList(manager.decode(ResourceLocation[].class, object.get("enchantments"))) : null;
                final List<ResourceLocation> selected = allowed;
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    List<Enchantment> candidates = new ArrayList<>();
                    for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS) {
                        if (enchantment.getRegistryName() == null || !enchantment.canApply(copy)) continue;
                        if (selected != null && !selected.contains(enchantment.getRegistryName())) continue;
                        candidates.add(enchantment);
                    }
                    if (candidates.isEmpty()) return stack;
                    Enchantment chosen = candidates.get(context.getRandom().nextInt(candidates.size()));
                    int minimum = Math.max(1, chosen.getMinLevel());
                    int maximum = Math.max(minimum, chosen.getMaxLevel());
                    copy.addEnchantment(chosen, minimum + context.getRandom().nextInt(maximum - minimum + 1));
                    return copy;
                };
            }
            case "enchant_with_levels": {
                fields(object, "levels", "treasure");
                DataRules.NumberValue levels = manager.decode(DataRules.NumberValue.class, required(object, "levels"));
                boolean treasure = object.has("treasure") && manager.decode(Boolean.class, object.get("treasure"));
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    double level = levels.evaluate(context);
                    if (!Double.isFinite(level) || level < 1 || level > Integer.MAX_VALUE) throw new IllegalArgumentException("Enchantment levels must be within 1.." + Integer.MAX_VALUE);
                    ItemStack copy = stack.copy();
                    List<EnchantmentData> selected = EnchantmentHelper.buildEnchantmentList(context.getRandom(), copy, (int) level, treasure);
                    if (selected == null) return stack;
                    for (EnchantmentData entry : selected) copy.addEnchantment(entry.enchantment, entry.enchantmentLevel);
                    return copy;
                };
            }
            case "looting_enchant": {
                fields(object, "count", "limit");
                DataRules.NumberValue count = manager.decode(DataRules.NumberValue.class, required(object, "count"));
                int limit = object.has("limit") ? manager.decode(Integer.class, object.get("limit")) : 0;
                if (limit < 0) throw error(object, "Looting limit must not be negative");
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    Entity killer = context.getLootContext() == null ? null : context.getLootContext().getKiller();
                    int looting = killer instanceof EntityLivingBase ? EnchantmentHelper.getLootingModifier((EntityLivingBase) killer) : 0;
                    if (looting <= 0) return stack;
                    double bonusValue = count.evaluate(context) * looting;
                    if (!Double.isFinite(bonusValue) || bonusValue < 0 || bonusValue > Integer.MAX_VALUE) throw new IllegalArgumentException("Looting bonus must be a nonnegative integer-sized value");
                    int bonus = (int) Math.floor(bonusValue);
                    if (context.getRandom().nextFloat() < bonusValue - bonus) bonus++;
                    if (bonus == 0) return stack;
                    int total = stack.getCount() + bonus;
                    if (limit > 0) total = Math.min(limit, total);
                    ItemStack copy = stack.copy();
                    copy.setCount(Math.min(copy.getMaxStackSize(), total));
                    return copy;
                };
            }
            case "apply_bonus": {
                fields(object, "enchantment", "formula", "bonus", "probability");
                ResourceLocation id = manager.decode(ResourceLocation.class, required(object, "enchantment"));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment == null) throw error(object, "Unknown enchantment " + id);
                String formula = manager.decode(String.class, required(object, "formula"));
                if (!("ore_drops".equals(formula) || "uniform".equals(formula) || "binomial".equals(formula))) throw error(object, "Unknown bonus formula " + formula);
                if ("ore_drops".equals(formula) && (object.has("bonus") || object.has("probability")) || "uniform".equals(formula) && object.has("probability")) throw error(object, "Unexpected parameters for " + formula);
                DataRules.NumberValue bonus = object.has("bonus") ? manager.decode(DataRules.NumberValue.class, object.get("bonus")) : context -> 1;
                DataRules.NumberValue probability = "binomial".equals(formula) ? manager.decode(DataRules.NumberValue.class, required(object, "probability")) : null;
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    int level = EnchantmentHelper.getEnchantmentLevel(enchantment, context.getTool());
                    long count = stack.getCount();
                    if ("ore_drops".equals(formula)) {
                        if (level > 0) count *= Math.max(0, context.getRandom().nextInt(level + 2) - 1) + 1L;
                    } else {
                        double sampled = bonus.evaluate(context);
                        if (!Double.isFinite(sampled) || sampled < 0 || sampled > Integer.MAX_VALUE) throw new IllegalArgumentException("Bonus must be a nonnegative integer-sized value");
                        int extra = (int) Math.floor(sampled);
                        long rolls = "uniform".equals(formula) ? (long) extra * level : (long) extra + level;
                        if (rolls >= Integer.MAX_VALUE) throw new IllegalArgumentException("Bonus sample exceeds integer range");
                        if (probability == null) count += context.getRandom().nextInt((int) rolls + 1);
                        else {
                            double chance = probability.evaluate(context);
                            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Bonus probability must be within 0..1");
                            for (int i = 0; i < rolls; i++) if (context.getRandom().nextFloat() < chance) count++;
                        }
                    }
                    if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Bonus count exceeds integer range");
                    ItemStack copy = stack.copy(); copy.setCount((int) count); return copy;
                };
            }
            case "set_name": {
                fields(object, "name");
                ITextComponent name = manager.decode(ITextComponent.class, required(object, "name"));
                return (stack, context) -> { if (stack.isEmpty()) return stack; ItemStack copy = stack.copy(); copy.setStackDisplayName(name.getFormattedText()); return copy; };
            }
            case "set_lore": {
                fields(object, "lore", "replace");
                ITextComponent[] lines = manager.decode(ITextComponent[].class, required(object, "lore"));
                String[] text = new String[lines.length];
                for (int i = 0; i < text.length; i++) text[i] = lines[i].getFormattedText();
                boolean replace = object.has("replace") && manager.decode(Boolean.class, object.get("replace"));
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound display = copy.getOrCreateSubCompound("display");
                    NBTTagList lore = replace ? new NBTTagList() : display.getTagList("Lore", 8);
                    for (String line : text) lore.appendTag(new net.minecraft.nbt.NBTTagString(line));
                    display.setTag("Lore", lore);
                    return copy;
                };
            }
            case "copy_nbt": {
                fields(object, "source", "path", "target", "storage", "operation");
                String source = manager.decode(String.class, required(object, "source"));
                if (!("entity".equals(source) || "block_entity".equals(source) || "tool".equals(source) || "inputs".equals(source) || "storage".equals(source))) throw error(object, "Unknown NBT source " + source);
                if (!"storage".equals(source) && object.has("storage")) throw error(object, "Storage id requires the storage source");
                ResourceLocation storage = "storage".equals(source) ? manager.decode(ResourceLocation.class, required(object, "storage")) : null;
                FunctionDataPath.Compiled path = manager.decode(FunctionDataPath.Compiled.class, required(object, "path"));
                FunctionDataPath.Compiled target = manager.decode(FunctionDataPath.Compiled.class, required(object, "target"));
                String operation = object.has("operation") ? manager.decode(String.class, object.get("operation")) : "replace";
                if (!("replace".equals(operation) || "merge".equals(operation) || "append".equals(operation))) throw error(object, "Unknown NBT copy operation " + operation);
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    NBTBase selected;
                    if (storage != null) selected = context.getWorld() == null ? null : FunctionStorage.get(context.getWorld()).get(storage, path);
                    else if ("inputs".equals(source)) selected = context.getInput(path);
                    else {
                        NBTTagCompound root = null;
                        if ("entity".equals(source) && context.getEntity() != null) root = context.getEntity().writeToNBT(new NBTTagCompound());
                        else if ("tool".equals(source) && !context.getTool().isEmpty()) root = context.getTool().writeToNBT(new NBTTagCompound());
                        else if ("block_entity".equals(source) && context.getWorld() != null && context.getOrigin() != null) {
                            TileEntity tile = context.getWorld().getTileEntity(context.getOrigin());
                            if (tile != null) root = tile.writeToNBT(new NBTTagCompound());
                        }
                        selected = root == null ? null : path.get(root);
                    }
                    if (selected == null) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound tag = copy.hasTagCompound() ? copy.getTagCompound() : new NBTTagCompound();
                    if ("replace".equals(operation)) target.set(tag, selected.copy());
                    else {
                        NBTBase existing = target.get(tag);
                        if ("merge".equals(operation)) {
                            if (!(selected instanceof NBTTagCompound) || existing != null && !(existing instanceof NBTTagCompound)) throw new IllegalArgumentException("NBT merge requires compound source and target");
                            NBTTagCompound merged = existing == null ? new NBTTagCompound() : (NBTTagCompound) existing;
                            merged.merge((NBTTagCompound) selected);
                            target.set(tag, merged);
                        } else {
                            if (existing != null && !(existing instanceof NBTTagList)) throw new IllegalArgumentException("NBT append target must be a list");
                            NBTTagList list = existing == null ? new NBTTagList() : (NBTTagList) existing;
                            if (list.tagCount() != 0 && list.getTagType() != selected.getId()) throw new IllegalArgumentException("NBT append must preserve the list element type");
                            list.appendTag(selected.copy()); target.set(tag, list);
                        }
                    }
                    copy.setTagCompound(tag); return copy;
                };
            }
            case "copy_name": {
                fields(object, "source");
                String source = manager.decode(String.class, required(object, "source"));
                if (!("this".equals(source) || "killer".equals(source) || "killer_player".equals(source) || "block_entity".equals(source))) {
                    throw error(object, "Unknown name source " + source);
                }
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ITextComponent name = null;
                    if ("this".equals(source)) {
                        if (context.getEntity() != null) name = context.getEntity().getDisplayName();
                    } else if ("killer".equals(source) || "killer_player".equals(source)) {
                        Entity killer = context.getLootContext() != null ? context.getLootContext().getKiller()
                                : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
                        if ("killer_player".equals(source) && !(killer instanceof net.minecraft.entity.player.EntityPlayer)) killer = null;
                        if (killer != null) name = killer.getDisplayName();
                    } else if (context.getWorld() != null && context.getOrigin() != null) {
                        TileEntity tile = context.getWorld().getTileEntity(context.getOrigin());
                        if (tile != null) name = tile.getDisplayName();
                    }
                    if (name == null) return stack;
                    ItemStack copy = stack.copy();
                    copy.setStackDisplayName(name.getFormattedText());
                    return copy;
                };
            }
            case "fill_player_head": {
                fields(object, "entity");
                String target = manager.decode(String.class, required(object, "entity"));
                if (!("this".equals(target) || "killer".equals(target) || "killer_player".equals(target))) {
                    throw error(object, "Unknown player head source " + target);
                }
                return (stack, context) -> {
                    if (stack.isEmpty() || stack.getItem() != net.minecraft.init.Items.SKULL || stack.getMetadata() != 3) return stack;
                    Entity entity;
                    if ("this".equals(target)) entity = context.getEntity();
                    else {
                        entity = context.getLootContext() != null ? context.getLootContext().getKiller()
                                : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
                        if ("killer_player".equals(target) && !(entity instanceof net.minecraft.entity.player.EntityPlayer)) entity = null;
                    }
                    if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)) return stack;
                    ItemStack copy = stack.copy();
                    copy.setTagInfo("SkullOwner", net.minecraft.nbt.NBTUtil.writeGameProfile(new NBTTagCompound(),
                            ((net.minecraft.entity.player.EntityPlayer) entity).getGameProfile()));
                    return copy;
                };
            }
            case "set_contents": {
                fields(object, "items", "block_entity");
                ItemStack[] items = manager.decode(ItemStack[].class, required(object, "items"));
                String blockEntityId = object.has("block_entity") ? manager.decode(String.class, object.get("block_entity")) : null;
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    NBTTagList list = new NBTTagList();
                    for (int index = 0; index < items.length; index++) {
                        if (items[index].isEmpty()) continue;
                        NBTTagCompound entry = items[index].writeToNBT(new NBTTagCompound());
                        entry.setByte("Slot", (byte) index);
                        list.appendTag(entry);
                    }
                    ItemStack copy = stack.copy();
                    NBTTagCompound blockEntity = copy.getOrCreateSubCompound("BlockEntityTag");
                    blockEntity.setTag("Items", list);
                    if (blockEntityId != null) blockEntity.setString("id", blockEntityId);
                    return copy;
                };
            }
            case "set_loot_table": {
                fields(object, "name", "seed");
                ResourceLocation table = manager.decode(ResourceLocation.class, required(object, "name"));
                long seed = object.has("seed") ? manager.decode(Long.class, object.get("seed")) : 0L;
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound blockEntity = copy.getOrCreateSubCompound("BlockEntityTag");
                    blockEntity.setString("LootTable", table.toString());
                    if (seed != 0L) blockEntity.setLong("LootTableSeed", seed);
                    return copy;
                };
            }
            default: throw error(object, "Unknown item transformation " + type);
        }
    }

    private static JsonElement required(JsonObject object, String name) throws JsonDeserializeException {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw error(object, "Missing modifier field " + name);
        return value;
    }
    private static void fields(JsonObject object, String... allowed) throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if ("type".equals(field.getKey()) || "conditions".equals(field.getKey())) continue;
            boolean found = false;
            for (String name : allowed) if (name.equals(field.getKey())) { found = true; break; }
            if (!found) throw error(object, "Unsupported modifier field " + field.getKey());
        }
    }
    private static JsonDeserializeException error(JsonElement value, String message) { return new JsonDeserializeException(value, message); }
}
