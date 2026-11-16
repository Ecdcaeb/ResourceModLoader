package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import rml.loader.deserialize.NativeTypeDecoders;

import java.util.*;

/** Compiled enchantments: every component has a native consumer, unknown components fail loading. */
public final class NativeEnchantment extends Enchantment {
    @FunctionalInterface public interface ValueEffect {
        float apply(float value, DataContext context);
        default boolean applies(DataContext context) { return true; }
    }
    private final int minimum, maximum;
    private final LevelBasedValue minCost, maxCost;
    private NativeTypeDecoders.ItemMatcher supported;
    private JsonObject pendingDefinition;
    private final Set<ResourceLocation> exclusive;
    private final Map<String, ValueEffect[]> values = new HashMap<>();
    private final Map<String, EntityEffect[]> entities = new HashMap<>();
    private EnchantmentAttribute[] attributes = new EnchantmentAttribute[0];
    final EntityEquipmentSlot[] activeSlots;
    private final boolean treasure, curse;
    private final DataContext itemContext = new DataContext(null, null, null, new Random(0), 0);

    public NativeEnchantment(ResourceLocation id, JsonObject json, DeserializerManager manager) throws JsonDeserializeException {
        this(id, json, manager, false);
    }

    NativeEnchantment(ResourceLocation id, JsonObject json, DeserializerManager manager, boolean deferred) throws JsonDeserializeException {
        super(rarity(json), EnumEnchantmentType.valueOf(text(json, "slot", "all").toUpperCase(Locale.ROOT)), slots(json));
        GameplayFields.only(json, "id", "weight", "slot", "slots", "min_level", "max_level", "min_cost", "max_cost", "supported_items", "exclusive_set", "treasure", "curse", "effects", "damage_bonus", "on_hit");
        activeSlots = slots(json);
        setRegistryName(id);
        setName(id.toString());
        minimum = integer(json, "min_level", 1);
        maximum = integer(json, "max_level", 1);
        minCost = json.has("min_cost") ? manager.decode(LevelBasedValue.class, json.get("min_cost")) : level -> 1 + (level - 1) * 10;
        maxCost = json.has("max_cost") ? manager.decode(LevelBasedValue.class, json.get("max_cost")) : level -> minCost.calculate(level) + 10;
        if (minimum < 1 || maximum < minimum || maximum > 32767) throw new IllegalArgumentException("Invalid enchantment levels: " + id);
        for (int level = minimum; level <= maximum; level++) {
            float low = minCost.calculate(level), high = maxCost.calculate(level);
            if (!Float.isFinite(low) || !Float.isFinite(high) || low < 0 || high < low || high > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid enchantment costs: " + id);
        }
        exclusive = new HashSet<>();
        if (json.has("exclusive_set")) for (JsonElement entry : json.getAsJsonArray("exclusive_set")) exclusive.add(manager.decode(ResourceLocation.class, entry));
        treasure = json.has("treasure") && json.get("treasure").getAsBoolean();
        curse = json.has("curse") && json.get("curse").getAsBoolean();
        pendingDefinition = json;
        if (!deferred) resolveDependencies(manager);
    }

    void resolveDependencies(DeserializerManager manager) throws JsonDeserializeException {
        JsonObject json = pendingDefinition;
        if (json == null) return;
        for (ResourceLocation id : exclusive) if (!Enchantment.REGISTRY.containsKey(id)) throw new IllegalArgumentException("Unknown exclusive enchantment: " + id);
        supported = json.has("supported_items") ? manager.decode(NativeTypeDecoders.ItemMatcher.class, json.get("supported_items")) : null;
        if (json.has("effects")) for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("effects").entrySet()) {
            ResourceLocation key = new ResourceLocation(entry.getKey());
            if (!"minecraft".equals(key.getNamespace()) && !"rml".equals(key.getNamespace())) throw new IllegalArgumentException("Unsupported enchantment component namespace: " + key);
            String component = key.getPath();
            if (values.containsKey(component) || entities.containsKey(component)) throw new IllegalArgumentException("Duplicate enchantment component: " + component);
            switch (component) {
                case "damage": case "damage_protection": case "protection": case "repair_with_xp": case "item_damage": case "experience":
                case "projectile_count": case "projectile_spread": case "projectile_speed": case "ammunition_use":
                    List<ValueEffect> compiled = new ArrayList<>();
                    for (JsonElement value : array(entry.getValue())) {
                        JsonObject wrapper = value.getAsJsonObject();
                        DataRules.Test condition = wrapper.has("requirements") ? manager.decode(DataRules.Test.class, wrapper.get("requirements")) : context -> true;
                        ValueEffect effect = manager.decode(ValueEffect.class, wrapper.has("effect") ? wrapper.get("effect") : value);
                        if (wrapper.has("effect")) GameplayFields.only(wrapper, "effect", "requirements");
                        compiled.add(new ValueEffect() {
                            @Override public boolean applies(DataContext context) { return condition.test(context) && effect.applies(context); }
                            @Override public float apply(float amount, DataContext context) { return effect.apply(amount, context); }
                        });
                    }
                    values.put(component, compiled.toArray(new ValueEffect[0]));
                    break;
                case "post_attack": case "post_defense": case "tick": case "location_changed": case "projectile_spawned":
                    List<EntityEffect> effects = new ArrayList<>();
                    for (JsonElement value : array(entry.getValue())) effects.add(EntityEffect.decode(manager, value.getAsJsonObject()));
                    entities.put(component, effects.toArray(new EntityEffect[0]));
                    break;
                case "attributes":
                    if (attributes.length != 0) throw new IllegalArgumentException("Duplicate attributes component");
                    attributes = manager.decode(EnchantmentAttribute[].class, entry.getValue());
                    Set<String> ids = new HashSet<>();
                    for (EnchantmentAttribute attribute : attributes) if (!ids.add(attribute.id)) throw new IllegalArgumentException("Duplicate enchantment attribute id: " + attribute.id);
                    break;
                default: throw new IllegalArgumentException("Unsupported enchantment component: " + entry.getKey());
            }
        }
        if (json.has("damage_bonus")) {
            LevelBasedValue bonus = manager.decode(LevelBasedValue.class, json.get("damage_bonus"));
            if (values.containsKey("damage")) throw new IllegalArgumentException("damage_bonus conflicts with damage component");
            values.put("damage", new ValueEffect[]{(value, context) -> value + bonus.calculate(context.getLevel()) * context.getLevel()});
        }
        if (json.has("on_hit")) {
            if (entities.containsKey("post_attack")) throw new IllegalArgumentException("on_hit conflicts with post_attack");
            entities.put("post_attack", new EntityEffect[]{new EntityEffect(context -> true, manager.decode(DataRules.Effect.class, json.get("on_hit")), "victim")});
        }
        pendingDefinition = null;
    }

    public boolean hasValue(String component) { return values.containsKey(component); }
    public boolean hasEntity(String component) { return entities.containsKey(component); }
    EnchantmentAttribute[] attributes() { return attributes; }
    /** NaN denotes an inapplicable repair component, distinct from an identity repair ratio. */
    float repairRatio(float value, DataContext context) {
        ValueEffect[] effects = values.get("repair_with_xp");
        boolean applicable = false;
        if (effects != null) for (ValueEffect effect : effects) if (effect.applies(context)) {
            value = effect.apply(value, context);
            applicable = true;
        }
        if (!Float.isFinite(value)) throw new IllegalStateException("Nonfinite repair ratio for " + getRegistryName());
        return applicable ? value : Float.NaN;
    }
    public float modify(String component, float value, DataContext context) {
        ValueEffect[] effects = values.get(component);
        if (effects != null) for (ValueEffect effect : effects) if (effect.applies(context)) value = effect.apply(value, context);
        if (!Float.isFinite(value)) throw new IllegalStateException("Nonfinite enchantment result for " + getRegistryName() + ":" + component);
        return value;
    }
    public void execute(String component, DataContext context, net.minecraft.entity.Entity owner, net.minecraft.entity.Entity attacker, net.minecraft.entity.Entity victim) {
        EntityEffect[] effects = entities.get(component);
        if (effects != null) for (EntityEffect effect : effects) {
            net.minecraft.entity.Entity target = "attacker".equals(effect.target) ? attacker : "victim".equals(effect.target) ? victim : owner;
            if (target != null && effect.condition.test(context)) effect.effect.apply(context, target);
        }
    }
    @Override public int getMinLevel() { return minimum; }
    @Override public int getMaxLevel() { return maximum; }
    @Override public int getMinEnchantability(int level) { return (int) minCost.calculate(level); }
    @Override public int getMaxEnchantability(int level) { return (int) maxCost.calculate(level); }
    @Override public boolean isTreasureEnchantment() { return treasure; }
    @Override public boolean isCurse() { return curse; }
    @Override public boolean canApply(ItemStack stack) { return !stack.isEmpty() && (supported == null ? super.canApply(stack) : supported.test(stack, itemContext)); }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return canApply(stack); }
    @Override protected boolean canApplyTogether(Enchantment other) {
        return super.canApplyTogether(other) && !exclusive.contains(other.getRegistryName())
                && (!(other instanceof NativeEnchantment) || !((NativeEnchantment) other).exclusive.contains(getRegistryName()));
    }
    private static final class EntityEffect {
        final DataRules.Test condition;
        final DataRules.Effect effect;
        final String target;
        EntityEffect(DataRules.Test condition, DataRules.Effect effect, String target) { this.condition = condition; this.effect = effect; this.target = target; }
        static EntityEffect decode(DeserializerManager manager, JsonObject json) throws JsonDeserializeException {
            if (json.has("effect")) GameplayFields.only(json, "effect", "requirements", "affected");
            String target = text(json, "affected", "enchanted");
            if (!Arrays.asList("enchanted", "attacker", "victim").contains(target)) throw new IllegalArgumentException("Unknown affected entity: " + target);
            return new EntityEffect(json.has("requirements") ? manager.decode(DataRules.Test.class, json.get("requirements")) : context -> true,
                    manager.decode(DataRules.Effect.class, json.has("effect") ? json.get("effect") : json), target);
        }
    }
    static Iterable<JsonElement> array(JsonElement value) { return value.isJsonArray() ? value.getAsJsonArray() : Collections.singletonList(value); }
    static String text(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static Rarity rarity(JsonObject json) {
        if (!json.has("weight")) return Rarity.COMMON;
        JsonElement weight = json.get("weight");
        if (weight.getAsJsonPrimitive().isNumber()) {
            int value = weight.getAsInt();
            for (Rarity rarity : Rarity.values()) if (rarity.getWeight() == value) return rarity;
            throw new IllegalArgumentException("Native enchantment weight must be 1, 2, 5, or 10");
        }
        return Rarity.valueOf(weight.getAsString().toUpperCase(Locale.ROOT));
    }
    private static EntityEquipmentSlot[] slots(JsonObject json) {
        if (!json.has("slots")) return EntityEquipmentSlot.values();
        Set<EntityEquipmentSlot> result = new LinkedHashSet<>();
        for (JsonElement value : array(json.get("slots"))) {
            String slot = value.getAsString();
            if ("any".equals(slot)) result.addAll(Arrays.asList(EntityEquipmentSlot.values()));
            else if ("hand".equals(slot)) result.addAll(Arrays.asList(EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND));
            else if ("armor".equals(slot)) result.addAll(Arrays.asList(EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET));
            else result.add(EntityEquipmentSlot.fromString(slot));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Enchantment slots must not be empty");
        return result.toArray(new EntityEquipmentSlot[0]);
    }
}
