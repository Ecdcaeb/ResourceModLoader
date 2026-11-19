package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static rml.loader.content.data.DecodeSupport.error;
import static rml.loader.content.data.DecodeSupport.fields;
import static rml.loader.content.data.DecodeSupport.fieldsWithConditions;
import static rml.loader.content.data.DecodeSupport.object;
import static rml.loader.content.data.DecodeSupport.required;

/** Native item operations that 1.12 does not expose through its loot serializers. */
final class NativeItemDecoders {
    private NativeItemDecoders() { }

    /** Returns null when the type belongs to another decoder family. */
    static DataRules.Modifier operation(DeserializerManager manager, JsonObject object, String type) throws JsonDeserializeException {
        switch (type) {
            case "furnace_smelt": {
                fieldsWithConditions(object);
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
                fieldsWithConditions(object, "enchantments");
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
                fieldsWithConditions(object, "levels", "treasure");
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
                fieldsWithConditions(object, "count", "limit");
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
            case "copy_name": {
                fieldsWithConditions(object, "source");
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
                        Entity killer = DataTargets.killer(context);
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
                fieldsWithConditions(object, "entity");
                String target = manager.decode(String.class, required(object, "entity"));
                if (!("this".equals(target) || "killer".equals(target) || "killer_player".equals(target))) {
                    throw error(object, "Unknown player head source " + target);
                }
                return (stack, context) -> {
                    if (stack.isEmpty() || stack.getItem() != net.minecraft.init.Items.SKULL || stack.getMetadata() != 3) return stack;
                    Entity entity;
                    if ("this".equals(target)) entity = context.getEntity();
                    else {
                        entity = DataTargets.killer(context);
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
                fieldsWithConditions(object, "items", "block_entity");
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
                fieldsWithConditions(object, "name", "seed");
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
            case "set_attributes": {
                fieldsWithConditions(object, "modifiers");
                JsonElement definitions = required(object, "modifiers");
                if (!definitions.isJsonArray()) throw error(object, "Attribute modifiers must be an array");
                List<NBTTagCompound> attributeTags = new ArrayList<>();
                for (JsonElement element : definitions.getAsJsonArray()) {
                    JsonObject modifier = object(element);
                    fields(modifier, "attribute", "name", "amount", "operation", "slot", "uuid");
                    String attribute = manager.decode(String.class, required(modifier, "attribute"));
                    if (attribute.isEmpty()) throw error(modifier, "Attribute name must not be empty");
                    String name = manager.decode(String.class, required(modifier, "name"));
                    double amount = manager.decode(Double.class, required(modifier, "amount"));
                    if (!Double.isFinite(amount)) throw error(modifier, "Attribute amount must be finite");
                    String operation = manager.decode(String.class, required(modifier, "operation"));
                    final int operationId;
                    switch (operation) {
                        case "addition": operationId = 0; break;
                        case "multiply_base": operationId = 1; break;
                        case "multiply_total": operationId = 2; break;
                        default: throw error(modifier, "Unknown attribute operation " + operation);
                    }
                    String slot = modifier.has("slot") ? manager.decode(String.class, modifier.get("slot")) : null;
                    if (slot != null && !("mainhand".equals(slot) || "offhand".equals(slot) || "head".equals(slot)
                            || "chest".equals(slot) || "legs".equals(slot) || "feet".equals(slot))) {
                        throw error(modifier, "Unknown attribute slot " + slot);
                    }
                    final UUID uuid;
                    if (modifier.has("uuid")) {
                        try { uuid = UUID.fromString(manager.decode(String.class, modifier.get("uuid"))); }
                        catch (IllegalArgumentException failure) { throw error(modifier, "Invalid attribute UUID"); }
                    } else {
                        uuid = UUID.nameUUIDFromBytes((attribute + "|" + name + "|" + (slot == null ? "" : slot)).getBytes(StandardCharsets.UTF_8));
                    }
                    NBTTagCompound attributeTag = new NBTTagCompound();
                    attributeTag.setString("AttributeName", attribute);
                    attributeTag.setString("Name", name);
                    attributeTag.setDouble("Amount", amount);
                    attributeTag.setInteger("Operation", operationId);
                    attributeTag.setLong("UUIDMost", uuid.getMostSignificantBits());
                    attributeTag.setLong("UUIDLeast", uuid.getLeastSignificantBits());
                    if (slot != null) attributeTag.setString("Slot", slot);
                    attributeTags.add(attributeTag);
                }
                if (attributeTags.isEmpty()) throw error(object, "set_attributes requires at least one modifier");
                return (stack, context) -> {
                    if (stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound tag = copy.hasTagCompound() ? copy.getTagCompound().copy() : new NBTTagCompound();
                    NBTTagList list = new NBTTagList();
                    for (NBTTagCompound attribute : attributeTags) list.appendTag(attribute.copy());
                    tag.setTag("AttributeModifiers", list);
                    copy.setTagCompound(tag);
                    return copy;
                };
            }
            default:
                return null;
        }
    }
}
