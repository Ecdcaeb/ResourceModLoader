package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.loader.content.data.DataRules;

public final class NativeItemDefinition {
    public final ResourceLocation id;
    public final ResourceLocation model;
    public final int maxStackSize;
    public final int maxDamage;
    public final String creativeTab;
    public final String harvestTool;
    public final int harvestLevel;
    public final float destroySpeed;
    public final String equipmentSlot;
    public final String useAction;
    public final int useDuration;
    public final int nutrition;
    public final float saturation;
    public final boolean alwaysEdible;
    public DataRules.Effect useEffect;
    private com.google.gson.JsonElement pendingUseEffect;

    private NativeItemDefinition(ResourceLocation id, ResourceLocation model, int maxStackSize, int maxDamage,
                                 String creativeTab, String harvestTool, int harvestLevel, float destroySpeed,
                                 String equipmentSlot, String useAction, int useDuration, int nutrition,
                                 float saturation, boolean alwaysEdible, DataRules.Effect useEffect) {
        this.id = id; this.model = model; this.maxStackSize = maxStackSize; this.maxDamage = maxDamage;
        this.creativeTab = creativeTab; this.harvestTool = harvestTool; this.harvestLevel = harvestLevel;
        this.destroySpeed = destroySpeed; this.equipmentSlot = equipmentSlot; this.useAction = useAction;
        this.useDuration = useDuration; this.nutrition = nutrition; this.saturation = saturation;
        this.alwaysEdible = alwaysEdible; this.useEffect = useEffect;
    }

    public static NativeItemDefinition decode(DeserializerManager manager, ResourceLocation id, JsonObject json)
            throws rml.deserializer.JsonDeserializeException {
        return decode(manager, id, json, false);
    }

    static NativeItemDefinition decodeForRegistration(DeserializerManager manager, ResourceLocation id, JsonObject json) throws rml.deserializer.JsonDeserializeException {
        return decode(manager, id, json, true);
    }

    void resolveDependencies(DeserializerManager manager) throws rml.deserializer.JsonDeserializeException {
        if (pendingUseEffect != null) {
            useEffect = manager.decode(DataRules.Effect.class, pendingUseEffect);
            pendingUseEffect = null;
        }
    }

    private static NativeItemDefinition decode(DeserializerManager manager, ResourceLocation id, JsonObject json, boolean deferred) throws rml.deserializer.JsonDeserializeException {
        GameplayFields.only(json, "id", "model", "max_stack_size", "max_damage", "creative_tab", "harvest_tool", "harvest_level", "destroy_speed", "equipment_slot", "use_action", "use_duration", "food", "on_use");
        ResourceLocation model = manager.decode(ResourceLocation.class, required(json, "model"));
        int stackSize = integer(json, "max_stack_size", 64);
        int damage = integer(json, "max_damage", 0);
        String tab = string(json, "creative_tab", "misc");
        String tool = string(json, "harvest_tool", "");
        int harvest = integer(json, "harvest_level", -1);
        float speed = json.has("destroy_speed") ? json.get("destroy_speed").getAsFloat() : 1.0F;
        String equipment = string(json, "equipment_slot", "");
        String useAction = string(json, "use_action", "none");
        int duration = integer(json, "use_duration", 0);
        int nutrition = 0;
        float saturation = 0;
        boolean alwaysEdible = false;
        if (json.has("food")) {
            JsonObject food = json.getAsJsonObject("food");
            GameplayFields.only(food, "nutrition", "saturation", "always_edible");
            nutrition = integer(food, "nutrition", -1);
            saturation = food.has("saturation") ? food.get("saturation").getAsFloat() : -1;
            alwaysEdible = food.has("always_edible") && food.get("always_edible").getAsBoolean();
        }
        DataRules.Effect useEffect = !deferred && json.has("on_use") ? manager.decode(DataRules.Effect.class, json.get("on_use")) : null;
        if (nutrition > 0 && "none".equals(useAction)) useAction = "eat";
        if (json.has("on_use") && "none".equals(useAction)) useAction = "drink";
        if (model == null || stackSize < 1 || stackSize > 64 || damage < 0 || harvest < -1 || speed < 0 || Float.isNaN(speed)
                || Float.isInfinite(speed) || duration < 0 || nutrition < 0 || nutrition > 20 || saturation < 0 || !Float.isFinite(saturation)) {
            throw new IllegalArgumentException("invalid item stack, tool, food, or use values");
        }
        if (!equipment.isEmpty() && !equipment.matches("head|chest|legs|feet")) throw new IllegalArgumentException("equipment_slot must be an armor slot");
        if (!useAction.matches("none|eat|drink|block")) throw new IllegalArgumentException("invalid use_action for Forge 1.12.2");
        if ("block".equals(useAction) && duration < 1) throw new IllegalArgumentException("block use_action requires positive use_duration");
        if ((nutrition > 0 || saturation > 0 || json.has("on_use")) && duration == 0) duration = 32;
        if (damage > 0) stackSize = 1;
        if (!java.util.Arrays.asList("misc", "building_blocks", "decorations", "redstone", "transportation", "tools", "combat", "brewing", "materials", "food").contains(tab)) {
            throw new IllegalArgumentException("unknown creative_tab: " + tab);
        }
        if (!tool.isEmpty() && !java.util.Arrays.asList("pickaxe", "axe", "shovel", "hoe", "sword").contains(tool)) {
            throw new IllegalArgumentException("unsupported harvest_tool: " + tool);
        }
        NativeItemDefinition result = new NativeItemDefinition(id, model, stackSize, damage, tab, tool, harvest, speed, equipment,
                useAction, duration, nutrition, saturation, alwaysEdible, useEffect);
        if (deferred) result.pendingUseEffect = json.get("on_use");
        return result;
    }

    private static com.google.gson.JsonElement required(JsonObject json, String key) {
        com.google.gson.JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("item requires " + key);
        return value;
    }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
}