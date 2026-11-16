package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.loader.content.data.DataRules;

public final class NativeBlockDefinition {
    public final ResourceLocation id;
    public final ResourceLocation model;
    public final Material material;
    public final SoundType sound;
    public final float hardness;
    public final float resistance;
    public final float light;
    public final int lightOpacity;
    public final String harvestTool;
    public final int harvestLevel;
    public final int variants;
    public final int defaultVariant;
    public final DataRules.Effect interactEffect;

    private NativeBlockDefinition(ResourceLocation id, ResourceLocation model, Material material, SoundType sound,
                                  float hardness, float resistance, float light, int lightOpacity,
                                  String harvestTool, int harvestLevel, int variants, int defaultVariant,
                                  DataRules.Effect interactEffect) {
        this.id = id; this.model = model; this.material = material; this.sound = sound;
        this.hardness = hardness; this.resistance = resistance; this.light = light; this.lightOpacity = lightOpacity;
        this.harvestTool = harvestTool; this.harvestLevel = harvestLevel; this.variants = variants;
        this.defaultVariant = defaultVariant; this.interactEffect = interactEffect;
    }

    public static NativeBlockDefinition decode(DeserializerManager manager, ResourceLocation id, JsonObject json)
            throws rml.deserializer.JsonDeserializeException {
        ResourceLocation model = manager.decode(ResourceLocation.class, required(json, "model"));
        Material material = material(required(json, "material").getAsString());
        SoundType sound = sound(json.has("sound") ? json.get("sound").getAsString() : "stone");
        float hardness = number(json, "hardness", -1);
        float resistance = number(json, "resistance", -1);
        float light = number(json, "light", 0);
        int opacity = integer(json, "light_opacity", 255);
        String harvestTool = string(json, "harvest_tool", "");
        int harvestLevel = integer(json, "harvest_level", -1);
        int variants = integer(json, "variants", 1);
        int defaultVariant = integer(json, "default_variant", 0);
        DataRules.Effect interact = json.has("on_interact") ? manager.decode(DataRules.Effect.class, json.get("on_interact")) : null;
        if (model == null || (hardness < 0 && hardness != -1) || Float.isNaN(hardness) || Float.isInfinite(hardness)
                || resistance < 0 || Float.isNaN(resistance) || Float.isInfinite(resistance)
                || light < 0 || light > 1 || Float.isNaN(light) || Float.isInfinite(light) || opacity < 0 || opacity > 255
                || harvestLevel < -1 || variants < 1 || variants > 16 || defaultVariant < 0 || defaultVariant >= variants) {
            throw new IllegalArgumentException("invalid block material, light, harvest, or state values");
        }
        if (harvestLevel >= 0 && harvestTool.isEmpty()) throw new IllegalArgumentException("harvest_tool is required when harvest_level is set");
        return new NativeBlockDefinition(id, model, material, sound, hardness, resistance, light, opacity,
                harvestTool, harvestLevel, variants, defaultVariant, interact);
    }

    private static Material material(String key) {
        switch (key) {
            case "rock": return Material.ROCK;
            case "wood": return Material.WOOD;
            case "ground": return Material.GROUND;
            case "grass": return Material.GRASS;
            case "iron": return Material.IRON;
            case "clay": return Material.CLAY;
            case "sand": return Material.SAND;
            case "glass": return Material.GLASS;
            case "tnt": return Material.TNT;
            case "leaves": return Material.LEAVES;
            case "plants": return Material.PLANTS;
            case "circuits": return Material.CIRCUITS;
            default: throw new IllegalArgumentException("unsupported block material: " + key);
        }
    }

    private static SoundType sound(String key) {
        switch (key) {
            case "stone": return SoundType.STONE;
            case "wood": return SoundType.WOOD;
            case "metal": return SoundType.METAL;
            case "glass": return SoundType.GLASS;
            case "cloth": return SoundType.CLOTH;
            case "sand": return SoundType.SAND;
            case "gravel": return SoundType.GROUND;
            case "plant": return SoundType.PLANT;
            case "snow": return SoundType.SNOW;
            case "ladder": return SoundType.LADDER;
            case "anvil": return SoundType.ANVIL;
            default: throw new IllegalArgumentException("unsupported block sound: " + key);
        }
    }

    private static JsonElement required(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("block requires " + key);
        return value;
    }
    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static float number(JsonObject json, String key, float fallback) { return json.has(key) ? json.get(key).getAsFloat() : fallback; }
}