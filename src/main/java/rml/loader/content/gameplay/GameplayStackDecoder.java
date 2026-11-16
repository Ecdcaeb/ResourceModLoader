package rml.loader.content.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;

import java.util.Map;

/** Decodes the compiler's modern item-stack result envelope into a no-loss 1.12 stack. */
final class GameplayStackDecoder {
    private GameplayStackDecoder() { }

    static ItemStack decode(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("item stack result must be an object");
        JsonObject source = value.getAsJsonObject();
        if (!source.has("id") && !source.has("item")) throw new IllegalArgumentException("item stack result requires id");
        String itemId = source.has("id") ? source.get("id").getAsString() : source.get("item").getAsString();
        JsonObject nativeStack = new JsonObject();
        nativeStack.addProperty("item", itemId);
        if (source.has("count")) nativeStack.add("count", source.get("count"));
        if (source.has("data")) nativeStack.add("data", source.get("data"));
        NBTTagCompound nbt = source.has("nbt") ? compound(manager, source.get("nbt")) : new NBTTagCompound();
        if (source.has("components")) applyComponents(manager, source.getAsJsonObject("components"), nbt, nativeStack);
        if (!nbt.isEmpty()) nativeStack.addProperty("nbt", nbt.toString());
        ItemStack stack = manager.decode(ItemStack.class, nativeStack);
        if (stack.isEmpty()) throw new IllegalArgumentException("item stack result resolved to an empty stack");
        applyEnchantments(manager, source.getAsJsonObject("components"), stack, nbt);
        if (source.has("count")) {
            int count = source.get("count").getAsInt();
            if (count < 1 || count > 99) throw new IllegalArgumentException("result count must be in [1,99]");
            stack.setCount(count);
        }
        return stack;
    }

    private static void applyComponents(DeserializerManager manager, JsonObject components, NBTTagCompound tag, JsonObject nativeStack)
            throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            switch (key) {
                case "minecraft:custom_data":
                    merge(tag, compound(manager, value));
                    break;
                case "minecraft:custom_name":
                    display(tag).setString("Name", textJson(manager.decode(ITextComponent.class, value)));
                    break;
                case "minecraft:lore": {
                    NBTTagList lore = new NBTTagList();
                    JsonArray lines = value.getAsJsonArray();
                    for (JsonElement line : lines) lore.appendTag(new NBTTagString(textJson(manager.decode(ITextComponent.class, line))));
                    display(tag).setTag("Lore", lore);
                    break;
                }
                case "minecraft:damage":
                    if (nativeStack.has("data")) throw new IllegalArgumentException("result cannot specify both data and minecraft:damage");
                    nativeStack.add("data", value);
                    break;
                case "minecraft:unbreakable": {
                    boolean enabled = value.isJsonObject() || value.getAsBoolean();
                    tag.setBoolean("Unbreakable", enabled);
                    if (value.isJsonObject() && value.getAsJsonObject().has("show_in_tooltip") && !value.getAsJsonObject().get("show_in_tooltip").getAsBoolean()) tag.setInteger("HideFlags", tag.getInteger("HideFlags") | 4);
                    break;
                }
                case "minecraft:repair_cost": tag.setInteger("RepairCost", value.getAsInt()); break;
                case "minecraft:can_break":
                case "minecraft:can_place_on": {
                    if (value.isJsonObject() && value.getAsJsonObject().has("show_in_tooltip")
                            && !value.getAsJsonObject().get("show_in_tooltip").getAsBoolean()) {
                        int bit = "minecraft:can_break".equals(key) ? 8 : 16;
                        tag.setInteger("HideFlags", tag.getInteger("HideFlags") | bit);
                    }
                    JsonArray blocks = blockIds(value);
                    NBTTagList list = new NBTTagList();
                    for (JsonElement block : blocks) list.appendTag(new NBTTagString(block.getAsString()));
                    tag.setTag("minecraft:can_break".equals(key) ? "CanDestroy" : "CanPlaceOn", list);
                    break;
                }
                case "minecraft:provides_trim_material":
                case "provides_trim_material": {
                    ResourceLocation material = manager.decode(ResourceLocation.class, value);
                    tag.setString("rml_trim_material", material.toString());
                    break;
                }
                case "minecraft:enchantments": break;
                default: throw new IllegalArgumentException("Unsupported item component: " + key);
            }
        }
    }

    private static void applyEnchantments(DeserializerManager manager, JsonObject components, ItemStack stack, NBTTagCompound tag)
            throws JsonDeserializeException {
        if (components == null || !components.has("minecraft:enchantments")) return;
        JsonObject levels = components.getAsJsonObject("minecraft:enchantments");
        if (levels.has("levels")) {
            if (levels.has("show_in_tooltip") && !levels.get("show_in_tooltip").getAsBoolean()) {
                tag.setInteger("HideFlags", tag.getInteger("HideFlags") | 1);
                stack.setTagCompound(tag);
            }
            levels = levels.getAsJsonObject("levels");
        }
        for (Map.Entry<String, JsonElement> entry : levels.entrySet()) {
            ResourceLocation id = manager.decode(ResourceLocation.class, new com.google.gson.JsonPrimitive(entry.getKey()));
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null) throw new IllegalArgumentException("Unknown enchantment in item result: " + id);
            int level = entry.getValue().getAsInt();
            if (level < 1 || level > 32767) throw new IllegalArgumentException("enchantment level must be positive");
            stack.addEnchantment(enchantment, level);
        }
    }

    private static JsonArray blockIds(JsonElement value) {
        if (value.isJsonArray()) return value.getAsJsonArray();
        JsonArray result = new JsonArray();
        JsonObject options = value.getAsJsonObject();
        for (JsonElement predicate : options.getAsJsonArray("predicates")) {
            JsonElement blocks = predicate.getAsJsonObject().get("blocks");
            if (blocks.isJsonArray()) result.addAll(blocks.getAsJsonArray()); else result.add(blocks);
        }
        return result;
    }

    private static NBTTagCompound compound(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            try { return JsonToNBT.getTagFromJson(value.getAsString()); }
            catch (Exception cause) { throw new IllegalArgumentException("Invalid item stack SNBT", cause); }
        }
        return manager.decode(NBTTagCompound.class, value);
    }

    private static String textJson(ITextComponent component) {
        return new com.google.gson.JsonPrimitive(component.getFormattedText()).toString();
    }

    private static NBTTagCompound display(NBTTagCompound tag) {
        NBTTagCompound display = tag.getCompoundTag("display");
        tag.setTag("display", display);
        return display;
    }

    private static void merge(NBTTagCompound target, NBTTagCompound source) { target.merge(source); }

}