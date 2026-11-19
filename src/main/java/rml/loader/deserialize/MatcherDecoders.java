package rml.loader.deserialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.DoubleRange;
import rml.deserializer.value.IntRange;
import rml.loader.content.CompiledTagModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry-aware item, block and entity matchers with their boolean composition. */
final class MatcherDecoders {
    private static final ResourceLocation PLAYER = new ResourceLocation("minecraft", "player");

    private MatcherDecoders() { }

    static void register(DeserializerManager manager, CompiledTagModule tags) {
        manager.addDefaultEntry(new AbstractDeserializer<>(id("item_match"), NativeTypeDecoders.ItemMatcher.class,
                value -> itemMatcher(manager, tags, value)));
        manager.addDefaultEntry(new AbstractDeserializer<>(id("block_match"), NativeTypeDecoders.BlockMatcher.class,
                value -> blockMatcher(manager, tags, value)));
        manager.addDefaultEntry(new AbstractDeserializer<>(id("entity_match"), NativeTypeDecoders.EntityMatcher.class,
                value -> entityMatcher(manager, tags, value)));
        for (String operation : new String[]{"all_of", "any_of", "not"}) {
            manager.addEntry(new AbstractDeserializer<>(id(operation), NativeTypeDecoders.ItemMatcher.class,
                    value -> itemLogic(manager, NativeTypeDecoders.object(value), operation)));
            manager.addEntry(new AbstractDeserializer<>(id(operation), NativeTypeDecoders.BlockMatcher.class,
                    value -> blockLogic(manager, NativeTypeDecoders.object(value), operation)));
            manager.addEntry(new AbstractDeserializer<>(id(operation), NativeTypeDecoders.EntityMatcher.class,
                    value -> entityLogic(manager, NativeTypeDecoders.object(value), operation)));
        }
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("rml", name);
    }

    private static NativeTypeDecoders.ItemMatcher itemMatcher(DeserializerManager manager, CompiledTagModule tags, JsonElement value) throws JsonDeserializeException {
        JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
        Names names = names(manager, tags, "item", object == null ? value : object.get("items"));
        IntRange count = object != null && object.has("count") ? manager.decode(IntRange.class, object.get("count")) : null;
        IntRange damage = object != null && object.has("damage") ? manager.decode(IntRange.class, object.get("damage")) : null;
        IntRange durability = object != null && object.has("durability") ? manager.decode(IntRange.class, object.get("durability")) : null;
        IntRange maxDamage = object != null && object.has("max_damage") ? manager.decode(IntRange.class, object.get("max_damage")) : null;
        NBTTagCompound nbt = object != null && object.has("nbt") ? compoundMatcher(manager, object.get("nbt")) : null;
        final Map<Enchantment, DoubleRange> enchantments = new java.util.LinkedHashMap<>();
        if (object != null && object.has("enchantments")) {
            JsonObject entries = NativeTypeDecoders.object(object.get("enchantments"));
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                ResourceLocation id = manager.decode(ResourceLocation.class, new com.google.gson.JsonPrimitive(entry.getKey()));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment == null) throw NativeTypeDecoders.error(value, "Unknown enchantment " + id);
                enchantments.put(enchantment, manager.decode(DoubleRange.class, entry.getValue()));
            }
        }
        return (stack, context) -> !stack.isEmpty() && names.test(stack.getItem().getRegistryName())
                && (count == null || count.test(stack.getCount())) && (damage == null || damage.test(stack.getItemDamage()))
                && (durability == null || durability.test(stack.getMaxDamage() - stack.getItemDamage()))
                && (maxDamage == null || maxDamage.test(stack.getMaxDamage()))
                && (nbt == null || NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true))
                && enchantmentsMatch(enchantments, stack);
    }

    private static boolean enchantmentsMatch(Map<Enchantment, DoubleRange> expected, ItemStack stack) {
        for (Map.Entry<Enchantment, DoubleRange> entry : expected.entrySet()) {
            if (!entry.getValue().test(EnchantmentHelper.getEnchantmentLevel(entry.getKey(), stack))) return false;
        }
        return true;
    }

    private static NativeTypeDecoders.BlockMatcher blockMatcher(DeserializerManager manager, CompiledTagModule tags, JsonElement value) throws JsonDeserializeException {
        JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
        Names names = names(manager, tags, "block", object == null ? value : object.get("blocks"));
        JsonObject properties = object != null && object.has("properties") ? NativeTypeDecoders.object(object.get("properties")) : new JsonObject();
        String[] propertyNames = new String[properties.size()];
        String[] propertyValues = new String[properties.size()];
        int propertyIndex = 0;
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            propertyNames[propertyIndex] = entry.getKey();
            propertyValues[propertyIndex++] = manager.decode(String.class, entry.getValue());
        }
        NBTTagCompound nbt = object != null && object.has("nbt") ? compoundMatcher(manager, object.get("nbt")) : null;
        return (world, position, state) -> {
            if (!names.test(state.getBlock().getRegistryName())) return false;
            for (int index = 0; index < propertyNames.length; index++) {
                IProperty<?> property = state.getBlock().getBlockState().getProperty(propertyNames[index]);
                if (property == null || !propertyValue(state, property).equals(propertyValues[index])) return false;
            }
            if (nbt == null) return true;
            TileEntity tile = world.getTileEntity(position);
            return tile != null && NBTUtil.areNBTEquals(nbt, tile.writeToNBT(new NBTTagCompound()), true);
        };
    }

    private static <T extends Comparable<T>> String propertyValue(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }

    private static final java.util.Set<String> EQUIPMENT_SLOTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "mainhand", "offhand", "head", "chest", "legs", "feet"));

    private static NativeTypeDecoders.EntityMatcher entityMatcher(DeserializerManager manager, CompiledTagModule tags, JsonElement value) throws JsonDeserializeException {
        JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
        Names names = names(manager, tags, "entity_type", object == null ? value : object.get("entity_types"));
        NBTTagCompound nbt = object != null && object.has("nbt") ? compoundMatcher(manager, object.get("nbt")) : null;
        DoubleRange health = object != null && object.has("health") ? manager.decode(DoubleRange.class, object.get("health")) : null;
        DoubleRange maxHealth = object != null && object.has("max_health") ? manager.decode(DoubleRange.class, object.get("max_health")) : null;
        DoubleRange absorption = object != null && object.has("absorption") ? manager.decode(DoubleRange.class, object.get("absorption")) : null;
        Boolean alive = object != null && object.has("alive") ? manager.decode(Boolean.class, object.get("alive")) : null;
        Boolean onFire = object != null && object.has("on_fire") ? manager.decode(Boolean.class, object.get("on_fire")) : null;
        final Map<String, NativeTypeDecoders.ItemMatcher> equipment = new java.util.LinkedHashMap<>();
        if (object != null && object.has("equipment")) {
            JsonObject slots = NativeTypeDecoders.object(object.get("equipment"));
            for (Map.Entry<String, JsonElement> entry : slots.entrySet()) {
                if (!EQUIPMENT_SLOTS.contains(entry.getKey())) throw NativeTypeDecoders.error(value, "Unknown equipment slot " + entry.getKey());
                equipment.put(entry.getKey(), manager.decode(NativeTypeDecoders.ItemMatcher.class, itemMatcherPayload(entry.getValue())));
            }
        }
        return (entity, context) -> {
            ResourceLocation key = entity instanceof EntityPlayer ? PLAYER : EntityList.getKey(entity);
            if (!names.test(key) || alive != null && entity.isEntityAlive() != alive || onFire != null && entity.isBurning() != onFire) return false;
            if (health != null && (!(entity instanceof EntityLivingBase) || !health.test(((EntityLivingBase) entity).getHealth()))) return false;
            if (maxHealth != null && (!(entity instanceof EntityLivingBase) || !maxHealth.test(((EntityLivingBase) entity).getMaxHealth()))) return false;
            if (absorption != null && (!(entity instanceof EntityLivingBase) || !absorption.test(((EntityLivingBase) entity).getAbsorptionAmount()))) return false;
            if (!equipment.isEmpty()) {
                if (!(entity instanceof EntityLivingBase)) return false;
                EntityLivingBase living = (EntityLivingBase) entity;
                for (Map.Entry<String, NativeTypeDecoders.ItemMatcher> entry : equipment.entrySet()) {
                    if (!entry.getValue().test(equipmentStack(living, entry.getKey()), context)) return false;
                }
            }
            if (nbt == null) return true;
            NBTTagCompound actual = new NBTTagCompound();
            entity.writeToNBT(actual);
            return NBTUtil.areNBTEquals(nbt, actual, true);
        };
    }

    private static ItemStack equipmentStack(EntityLivingBase entity, String slot) {
        switch (slot) {
            case "mainhand": return entity.getHeldItemMainhand();
            case "offhand": return entity.getHeldItemOffhand();
            case "head": return entity.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD);
            case "chest": return entity.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST);
            case "legs": return entity.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS);
            case "feet": return entity.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET);
            default: throw new IllegalArgumentException("Unknown equipment slot " + slot);
        }
    }

    private static JsonElement itemMatcherPayload(JsonElement value) {
        if (value != null && value.isJsonObject() && !value.getAsJsonObject().has("type")) {
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("type", "rml:item_match");
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                wrapped.add(entry.getKey(), entry.getValue());
            }
            return wrapped;
        }
        return value;
    }

    private static NBTTagCompound compoundMatcher(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        NBTBase decoded = manager.decode(NBTBase.class, value);
        if (!(decoded instanceof NBTTagCompound)) throw NativeTypeDecoders.error(value, "NBT matcher requires a compound");
        return (NBTTagCompound) decoded;
    }

    private static NativeTypeDecoders.ItemMatcher itemLogic(DeserializerManager manager, JsonObject object, String operation) throws JsonDeserializeException {
        if ("not".equals(operation)) {
            NativeTypeDecoders.ItemMatcher child = manager.decode(NativeTypeDecoders.ItemMatcher.class, NativeTypeDecoders.required(object, "term"));
            return (stack, context) -> !child.test(stack, context);
        }
        NativeTypeDecoders.ItemMatcher[] terms = manager.decode(NativeTypeDecoders.ItemMatcher[].class, NativeTypeDecoders.required(object, "terms"));
        boolean all = "all_of".equals(operation);
        return (stack, context) -> { for (NativeTypeDecoders.ItemMatcher term : terms) if (term.test(stack, context) != all) return !all; return all; };
    }

    private static NativeTypeDecoders.BlockMatcher blockLogic(DeserializerManager manager, JsonObject object, String operation) throws JsonDeserializeException {
        if ("not".equals(operation)) {
            NativeTypeDecoders.BlockMatcher child = manager.decode(NativeTypeDecoders.BlockMatcher.class, NativeTypeDecoders.required(object, "term"));
            return (world, position, state) -> !child.test(world, position, state);
        }
        NativeTypeDecoders.BlockMatcher[] terms = manager.decode(NativeTypeDecoders.BlockMatcher[].class, NativeTypeDecoders.required(object, "terms"));
        boolean all = "all_of".equals(operation);
        return (world, position, state) -> { for (NativeTypeDecoders.BlockMatcher term : terms) if (term.test(world, position, state) != all) return !all; return all; };
    }

    private static NativeTypeDecoders.EntityMatcher entityLogic(DeserializerManager manager, JsonObject object, String operation) throws JsonDeserializeException {
        if ("not".equals(operation)) {
            NativeTypeDecoders.EntityMatcher child = manager.decode(NativeTypeDecoders.EntityMatcher.class, NativeTypeDecoders.required(object, "term"));
            return (entity, context) -> !child.test(entity, context);
        }
        NativeTypeDecoders.EntityMatcher[] terms = manager.decode(NativeTypeDecoders.EntityMatcher[].class, NativeTypeDecoders.required(object, "terms"));
        boolean all = "all_of".equals(operation);
        return (entity, context) -> { for (NativeTypeDecoders.EntityMatcher term : terms) if (term.test(entity, context) != all) return !all; return all; };
    }

    private static Names names(DeserializerManager manager, CompiledTagModule tags, String registry, JsonElement value) throws JsonDeserializeException {
        Set<ResourceLocation> ids = new HashSet<>();
        List<ResourceLocation> groups = new ArrayList<>();
        if (value != null) {
            String[] entries = manager.decode(String[].class, value);
            for (String entry : entries) {
                if (entry.startsWith("#")) groups.add(new ResourceLocation(entry.substring(1)));
                else {
                    ResourceLocation key = new ResourceLocation(entry);
                    boolean known = "item".equals(registry) ? ForgeRegistries.ITEMS.containsKey(key)
                            : "block".equals(registry) ? ForgeRegistries.BLOCKS.containsKey(key)
                            : PLAYER.equals(key) || ForgeRegistries.ENTITIES.containsKey(key);
                    if (!known) throw NativeTypeDecoders.error(value, "Unknown " + registry + " in matcher: " + key);
                    ids.add(key);
                }
            }
        }
        return new Names(registry, tags, ids, groups, value == null);
    }

    private static final class Names {
        private final String registry;
        private final CompiledTagModule tags;
        private final Set<ResourceLocation> ids;
        private final ResourceLocation[] groups;
        private final boolean any;

        private Names(String registry, CompiledTagModule tags, Set<ResourceLocation> ids, List<ResourceLocation> groups, boolean any) {
            this.registry = registry;
            this.tags = tags;
            this.ids = ids;
            this.groups = groups.toArray(new ResourceLocation[0]);
            this.any = any;
        }

        private boolean test(ResourceLocation key) {
            if (any) return true;
            if (key == null) return false;
            if (ids.contains(key)) return true;
            for (ResourceLocation tag : groups) if (tags.contains(registry, tag, key)) return true;
            return false;
        }
    }
}
