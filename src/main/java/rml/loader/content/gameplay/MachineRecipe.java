package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.loader.content.data.DataContext;
import rml.loader.deserialize.NativeTypeDecoders;

/** A station-specific recipe retaining its original operation and native result stack. */
public final class MachineRecipe {
    public final ResourceLocation id;
    public final String operation;
    public final NativeTypeDecoders.ItemMatcher[] ingredients;
    public final ItemStack result;
    public final ResourceLocation trimPattern;
    public final float experience;
    public final int cookingTime;
    private final TrimLookup trims;

    public interface TrimLookup {
        ResourceLocation resolveTrimMaterial(ItemStack addition, DataContext context);
        ResourceLocation resolveTrimPattern(ResourceLocation explicitPattern, ItemStack template);
    }

    private MachineRecipe(ResourceLocation id, String operation, NativeTypeDecoders.ItemMatcher[] ingredients,
                          ItemStack result, ResourceLocation trimPattern, float experience, int cookingTime, TrimLookup trims) {
        this.id = id; this.operation = operation; this.ingredients = ingredients;
        this.result = result; this.trimPattern = trimPattern; this.experience = experience;
        this.cookingTime = cookingTime; this.trims = trims;
    }

    public ItemStack resultFor(ItemStack template, ItemStack base, ItemStack addition, DataContext context) {
        if (!"smithing_trim".equals(operation)) return result;
        if (trims == null) throw new IllegalStateException("Smithing trim recipe has no Presentation trim resolver");
        ResourceLocation pattern = trims.resolveTrimPattern(trimPattern, template);
        ResourceLocation material = trims.resolveTrimMaterial(addition, context);
        if (pattern == null || material == null) throw new IllegalStateException("Could not resolve smithing trim material and pattern IDs");
        ItemStack trimmed = base.copy();
        net.minecraft.nbt.NBTTagCompound tag = trimmed.hasTagCompound() ? trimmed.getTagCompound().copy() : new net.minecraft.nbt.NBTTagCompound();
        net.minecraft.nbt.NBTTagCompound trim = new net.minecraft.nbt.NBTTagCompound();
        trim.setString("pattern", pattern.toString());
        trim.setString("material", material.toString());
        tag.setTag("rml_trim", trim);
        trimmed.setTagCompound(tag);
        return trimmed;
    }

    public static MachineRecipe decode(DeserializerManager manager, ResourceLocation id, JsonObject json, TrimLookup trims)
            throws rml.deserializer.JsonDeserializeException {
        String operation = path(manager.decode(ResourceLocation.class, json.get("type")));
        NativeTypeDecoders.ItemMatcher[] inputs;
        switch (operation) {
            case "blasting":
            case "smoking":
            case "campfire_cooking":
            case "stonecutting":
                inputs = new NativeTypeDecoders.ItemMatcher[]{decodeMatcher(manager, required(json, "ingredient"))};
                break;
            case "smithing_transform":
            case "smithing_trim":
                inputs = new NativeTypeDecoders.ItemMatcher[]{
                        decodeMatcher(manager, required(json, "template")),
                        decodeMatcher(manager, required(json, "base")),
                        decodeMatcher(manager, required(json, "addition"))};
                break;
            case "crafting_transmute":
                inputs = new NativeTypeDecoders.ItemMatcher[]{
                        decodeMatcher(manager, required(json, "input")),
                        decodeMatcher(manager, required(json, "material"))};
                break;
            default: throw new IllegalArgumentException("unsupported machine recipe operation: " + operation);
        }
        ResourceLocation trimPattern = "smithing_trim".equals(operation) && json.has("pattern")
                ? manager.decode(ResourceLocation.class, json.get("pattern")) : null;
        ItemStack result = "smithing_trim".equals(operation) ? ItemStack.EMPTY
                : GameplayStackDecoder.decode(manager, required(json, "result"));
        if (!"smithing_trim".equals(operation) && result.isEmpty()) throw new IllegalArgumentException("machine recipe result must not be empty");
        float experience = json.has("experience") ? json.get("experience").getAsFloat() : 0;
        int defaultTime = "campfire_cooking".equals(operation) ? 600 : 200;
        int cookingTime = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : defaultTime;
        if (experience < 0 || Float.isInfinite(experience) || Float.isNaN(experience) || cookingTime < 1) throw new IllegalArgumentException("experience must be nonnegative and cookingtime positive");
        return new MachineRecipe(id, operation, inputs, result.copy(), trimPattern, experience, cookingTime, trims);
    }

    private static NativeTypeDecoders.ItemMatcher decodeMatcher(DeserializerManager manager, JsonElement value)
            throws rml.deserializer.JsonDeserializeException {
        if (!value.isJsonArray()) return manager.decode(NativeTypeDecoders.ItemMatcher.class, value);
        final NativeTypeDecoders.ItemMatcher[] alternatives = manager.decode(NativeTypeDecoders.ItemMatcher[].class, value);
        if (alternatives.length == 0) throw new IllegalArgumentException("recipe ingredient alternatives must not be empty");
        return (stack, context) -> {
            for (NativeTypeDecoders.ItemMatcher alternative : alternatives) if (alternative.test(stack, context)) return true;
            return false;
        };
    }

    private static JsonElement required(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("missing recipe field " + key);
        return value;
    }
    private static String path(ResourceLocation location) { return location.getPath(); }
}