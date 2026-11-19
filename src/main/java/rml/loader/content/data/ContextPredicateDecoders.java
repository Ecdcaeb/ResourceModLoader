package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.DoubleRange;
import rml.loader.deserialize.NativeTypeDecoders;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static rml.loader.content.data.DecodeSupport.error;
import static rml.loader.content.data.DecodeSupport.fields;
import static rml.loader.content.data.DecodeSupport.object;
import static rml.loader.content.data.DecodeSupport.required;

/** Context predicates reusable by loot, enchantments, timelines and user-registered nodes. */
final class ContextPredicateDecoders {
    private ContextPredicateDecoders() { }
    @FunctionalInterface private interface Comparison { boolean test(double left, double right); }
    static void register(DeserializerManager manager) {
        for (String name : new String[]{"value_check", "compare", "time_check", "weather_check", "light_check", "match_tool",
                "match_entity", "match_block", "distance", "damage_source", "score_check", "survives_explosion", "table_bonus",
                "enchantment_level", "enchantments", "held_item", "entity_properties", "entity_scores", "block_state_property", "location_check"}) {
            manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", name), DataRules.Test.class,
                    json -> decode(manager, json.getAsJsonObject())));
        }
    }

    private static DataRules.Test decode(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        String type = new ResourceLocation(object.get("type").getAsString()).getPath();
        switch (type) {
            case "value_check": {
                fields(object, "value", "range");
                DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                DoubleRange range = manager.decode(DoubleRange.class, required(object, "range"));
                return context -> range.test(value.evaluate(context));
            }
            case "compare": {
                fields(object, "left", "right", "operator");
                DataRules.NumberValue left = manager.decode(DataRules.NumberValue.class, required(object, "left"));
                DataRules.NumberValue right = manager.decode(DataRules.NumberValue.class, required(object, "right"));
                String operator = manager.decode(String.class, required(object, "operator"));
                final Comparison compare;
                switch (operator) {
                    case "=": compare = (a, b) -> a == b; break;
                    case "!=": compare = (a, b) -> a != b; break;
                    case "<": compare = (a, b) -> a < b; break;
                    case "<=": compare = (a, b) -> a <= b; break;
                    case ">": compare = (a, b) -> a > b; break;
                    case ">=": compare = (a, b) -> a >= b; break;
                    default: throw error(object, "Unknown numeric comparison " + operator);
                }
                return context -> compare.test(left.evaluate(context), right.evaluate(context));
            }
            case "time_check": {
                fields(object, "value", "period");
                DoubleRange range = manager.decode(DoubleRange.class, required(object, "value"));
                JsonElement periodValue = object.get("period");
                long period = periodValue == null ? 0 : periodValue.isJsonPrimitive() && periodValue.getAsJsonPrimitive().isNumber()
                        ? periodValue.getAsBigDecimal().longValueExact() : manager.decode(Long.class, periodValue);
                if (object.has("period") && period <= 0) throw error(object, "Time period must be positive");
                return context -> context.getWorld() != null && range.test(period == 0 ? context.getWorld().getWorldTime() : Math.floorMod(context.getWorld().getWorldTime(), period));
            }
            case "weather_check": {
                fields(object, "raining", "thundering");
                Boolean rain = optional(manager, object, "raining"), thunder = optional(manager, object, "thundering");
                return context -> context.getWorld() != null && (rain == null || context.getWorld().isRaining() == rain)
                        && (thunder == null || context.getWorld().isThundering() == thunder);
            }
            case "light_check": {
                fields(object, "light", "offset");
                DoubleRange range = manager.decode(DoubleRange.class, required(object, "light"));
                BlockPos offset = offset(manager, object);
                return context -> context.getWorld() != null && context.getOrigin() != null
                        && range.test(context.getWorld().getLight(context.getOrigin().add(offset)));
            }
            case "match_tool": {
                fields(object, "predicate");
                NativeTypeDecoders.ItemMatcher matcher = manager.decode(NativeTypeDecoders.ItemMatcher.class, required(object, "predicate"));
                return context -> matcher.test(context.getTool(), context);
            }
            case "match_entity": {
                fields(object, "predicate", "target");
                NativeTypeDecoders.EntityMatcher matcher = manager.decode(NativeTypeDecoders.EntityMatcher.class, required(object, "predicate"));
                String target = object.has("target") ? manager.decode(String.class, object.get("target")) : "this";
                if (!("this".equals(target) || "attacker".equals(target) || "direct_attacker".equals(target) || "killer".equals(target) || "killer_player".equals(target))) throw error(object, "Unknown entity context target " + target);
                return context -> {
                    Entity entity = DataTargets.entity(context, target);
                    return entity != null && matcher.test(entity, context);
                };
            }
            case "match_block": {
                fields(object, "predicate", "offset");
                NativeTypeDecoders.BlockMatcher matcher = manager.decode(NativeTypeDecoders.BlockMatcher.class, required(object, "predicate"));
                BlockPos offset = offset(manager, object);
                return context -> {
                    if (context.getWorld() == null || context.getOrigin() == null) return false;
                    BlockPos position = context.getOrigin().add(offset);
                    return context.getWorld().isBlockLoaded(position) && matcher.test(context.getWorld(), position, context.getWorld().getBlockState(position));
                };
            }
            case "distance": {
                fields(object, "x", "y", "z", "horizontal", "absolute");
                DoubleRange x = range(manager, object, "x"), y = range(manager, object, "y"), z = range(manager, object, "z");
                DoubleRange horizontal = range(manager, object, "horizontal"), absolute = range(manager, object, "absolute");
                return context -> {
                    Entity entity = context.getEntity();
                    BlockPos origin = context.getOrigin();
                    if (entity == null || origin == null) return false;
                    double dx = Math.abs(entity.posX - origin.getX()), dy = Math.abs(entity.posY - origin.getY()), dz = Math.abs(entity.posZ - origin.getZ());
                    return (x == null || x.test(dx)) && (y == null || y.test(dy)) && (z == null || z.test(dz))
                            && (horizontal == null || horizontal.test(Math.sqrt(dx * dx + dz * dz)))
                            && (absolute == null || absolute.test(Math.sqrt(dx * dx + dy * dy + dz * dz)));
                };
            }
            case "damage_source": {
                fields(object, "names", "fire", "projectile", "explosion", "magic", "bypasses_armor", "absolute", "direct");
                Set<String> names = object.has("names") ? new HashSet<>(java.util.Arrays.asList(manager.decode(String[].class, object.get("names")))) : null;
                Boolean fire = optional(manager, object, "fire"), projectile = optional(manager, object, "projectile"), explosion = optional(manager, object, "explosion");
                Boolean magic = optional(manager, object, "magic"), bypass = optional(manager, object, "bypasses_armor"), absolute = optional(manager, object, "absolute"), direct = optional(manager, object, "direct");
                return context -> {
                    DamageSource source = context.getDamageSource();
                    return source != null && (names == null || names.contains(source.getDamageType()))
                            && (fire == null || source.isFireDamage() == fire) && (projectile == null || source.isProjectile() == projectile)
                            && (explosion == null || source.isExplosion() == explosion) && (magic == null || source.isMagicDamage() == magic)
                            && (bypass == null || source.isUnblockable() == bypass) && (absolute == null || source.isDamageAbsolute() == absolute)
                            && (direct == null || (source.getImmediateSource() == source.getTrueSource()) == direct);
                };
            }
            case "score_check": {
                fields(object, "holder", "objective", "value");
                String holder = manager.decode(String.class, required(object, "holder"));
                String objective = manager.decode(String.class, required(object, "objective"));
                DoubleRange range = manager.decode(DoubleRange.class, required(object, "value"));
                return context -> {
                    if (context.getWorld() == null) return false;
                    String name = DataTargets.scoreHolder(context, holder);
                    if (name == null) return false;
                    Scoreboard board = context.getWorld().getScoreboard();
                    ScoreObjective score = board.getObjective(objective);
                    return score != null && board.entityHasObjective(name, score) && range.test(board.getOrCreateScore(name, score).getScorePoints());
                };
            }
            case "survives_explosion": {
                fields(object, "radius");
                DataRules.NumberValue radius = manager.decode(DataRules.NumberValue.class, required(object, "radius"));
                return context -> { double size = radius.evaluate(context); return size <= 0 || context.getRandom().nextFloat() <= 1.0D / size; };
            }
            case "table_bonus": {
                fields(object, "enchantment", "chances");
                ResourceLocation id = manager.decode(ResourceLocation.class, required(object, "enchantment"));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment == null) throw error(object, "Unknown enchantment " + id);
                DataRules.NumberValue[] chances = manager.decode(DataRules.NumberValue[].class, required(object, "chances"));
                if (chances.length == 0) throw error(object, "Enchantment chances must not be empty");
                return context -> {
                    int level = EnchantmentHelper.getEnchantmentLevel(enchantment, context.getTool());
                    double chance = chances[Math.min(level, chances.length - 1)].evaluate(context);
                    if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Enchantment chance must be within 0..1");
                    return context.getRandom().nextFloat() < chance;
                };
            }
            case "block_state_property": {
                fields(object, "block", "properties", "offset");
                NativeTypeDecoders.BlockMatcher matcher = manager.decode(NativeTypeDecoders.BlockMatcher.class,
                        blockMatcherPayload(required(object, "block"), object.get("properties")));
                BlockPos offset = offset(manager, object);
                return context -> {
                    if (context.getWorld() == null || context.getOrigin() == null) return false;
                    BlockPos position = context.getOrigin().add(offset);
                    return context.getWorld().isBlockLoaded(position)
                            && matcher.test(context.getWorld(), position, context.getWorld().getBlockState(position));
                };
            }
            case "location_check": {
                fields(object, "offset", "block", "light", "biome");
                NativeTypeDecoders.BlockMatcher matcher = object.has("block")
                        ? manager.decode(NativeTypeDecoders.BlockMatcher.class, blockMatcherPayload(required(object, "block"), null)) : null;
                DoubleRange light = object.has("light") ? manager.decode(DoubleRange.class, required(object, "light")) : null;
                String biome = object.has("biome") ? manager.decode(String.class, required(object, "biome")) : null;
                BlockPos offset = offset(manager, object);
                return context -> {
                    if (context.getWorld() == null || context.getOrigin() == null) return false;
                    BlockPos position = context.getOrigin().add(offset);
                    if (!context.getWorld().isBlockLoaded(position)) return false;
                    if (matcher != null && !matcher.test(context.getWorld(), position, context.getWorld().getBlockState(position))) return false;
                    if (light != null && !light.test(context.getWorld().getLight(position))) return false;
                    if (biome != null) {
                        ResourceLocation found = context.getWorld().getBiome(position).getRegistryName();
                        if (found == null || !biome.equals(found.toString())) return false;
                    }
                    return true;
                };
            }
            case "held_item": {
                fields(object, "predicate", "hand");
                NativeTypeDecoders.ItemMatcher matcher = manager.decode(NativeTypeDecoders.ItemMatcher.class, itemMatcherPayload(required(object, "predicate")));
                String hand = object.has("hand") ? manager.decode(String.class, object.get("hand")) : "mainhand";
                if (!("mainhand".equals(hand) || "offhand".equals(hand))) throw error(object, "Unknown hand " + hand);
                return context -> {
                    if (!(context.getEntity() instanceof net.minecraft.entity.EntityLivingBase)) return false;
                    net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) context.getEntity();
                    return matcher.test("offhand".equals(hand) ? living.getHeldItemOffhand() : living.getHeldItemMainhand(), context);
                };
            }
            case "entity_properties": {
                fields(object, "entity", "predicate", "flags");
                String target = object.has("entity") ? manager.decode(String.class, object.get("entity")) : "this";
                if (!("this".equals(target) || "killer".equals(target) || "killer_player".equals(target) || "attacker".equals(target) || "direct_attacker".equals(target))) {
                    throw error(object, "Unknown entity context target " + target);
                }
                NativeTypeDecoders.EntityMatcher matcher = manager.decode(NativeTypeDecoders.EntityMatcher.class, entityMatcherPayload(required(object, "predicate")));
                JsonObject flags = object.has("flags") ? object(required(object, "flags")) : null;
                Boolean fire = flags == null ? null : optional(manager, flags, "on_fire");
                Boolean sneaking = flags == null ? null : optional(manager, flags, "sneaking");
                Boolean sprinting = flags == null ? null : optional(manager, flags, "sprinting");
                Boolean baby = flags == null ? null : optional(manager, flags, "baby");
                if (flags != null) fields(flags, "on_fire", "sneaking", "sprinting", "baby");
                return context -> {
                    Entity entity = DataTargets.entity(context, target);
                    if (entity == null || !matcher.test(entity, context)) return false;
                    if (fire == null && sneaking == null && sprinting == null && baby == null) return true;
                    if (!(entity instanceof net.minecraft.entity.EntityLivingBase)) return false;
                    net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
                    return (fire == null || living.isBurning() == fire)
                            && (sneaking == null || living.isSneaking() == sneaking)
                            && (sprinting == null || living.isSprinting() == sprinting)
                            && (baby == null || living.isChild() == baby);
                };
            }
            case "entity_scores": {
                fields(object, "entity", "scores");
                String holder = manager.decode(String.class, required(object, "entity"));
                JsonObject scores = object(required(object, "scores"));
                Map<String, DoubleRange> ranges = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : scores.entrySet()) {
                    ranges.put(entry.getKey(), manager.decode(DoubleRange.class, entry.getValue()));
                }
                if (ranges.isEmpty()) throw error(object, "Entity scores must not be empty");
                return context -> {
                    if (context.getWorld() == null) return false;
                    String name = DataTargets.scoreHolder(context, holder);
                    if (name == null) return false;
                    Scoreboard board = context.getWorld().getScoreboard();
                    for (Map.Entry<String, DoubleRange> entry : ranges.entrySet()) {
                        ScoreObjective objective = board.getObjective(entry.getKey());
                        if (objective == null || !board.entityHasObjective(name, objective)) return false;
                        if (!entry.getValue().test(board.getOrCreateScore(name, objective).getScorePoints())) return false;
                    }
                    return true;
                };
            }
            case "enchantments": {
                fields(object, "enchantments");
                JsonObject definitions = object(required(object, "enchantments"));
                Map<Enchantment, DoubleRange> ranges = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : definitions.entrySet()) {
                    ResourceLocation id = manager.decode(ResourceLocation.class, new com.google.gson.JsonPrimitive(entry.getKey()));
                    Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                    if (enchantment == null) throw error(object, "Unknown enchantment " + id);
                    ranges.put(enchantment, manager.decode(DoubleRange.class, entry.getValue()));
                }
                if (ranges.isEmpty()) throw error(object, "Enchantments predicate requires at least one entry");
                return context -> {
                    for (Map.Entry<Enchantment, DoubleRange> entry : ranges.entrySet()) {
                        if (!entry.getValue().test(EnchantmentHelper.getEnchantmentLevel(entry.getKey(), context.getTool()))) return false;
                    }
                    return true;
                };
            }
            case "enchantment_level": {
                fields(object, "enchantment", "level");
                ResourceLocation id = manager.decode(ResourceLocation.class, required(object, "enchantment"));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment == null) throw error(object, "Unknown enchantment " + id);
                DoubleRange range = manager.decode(DoubleRange.class, required(object, "level"));
                return context -> range.test(EnchantmentHelper.getEnchantmentLevel(enchantment, context.getTool()));
            }
            default: throw error(object, "Unknown context predicate " + type);
        }
    }

    private static BlockPos offset(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        return object.has("offset") ? manager.decode(BlockPos.class, object.get("offset")) : BlockPos.ORIGIN;
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

    private static JsonElement blockMatcherPayload(JsonElement block, JsonElement properties) {
        if (block != null && block.isJsonObject() && block.getAsJsonObject().has("type")) return block;
        JsonObject wrapped = new JsonObject();
        wrapped.addProperty("type", "rml:block_match");
        if (block != null && block.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : block.getAsJsonObject().entrySet()) {
                wrapped.add(field.getKey(), field.getValue());
            }
        } else {
            wrapped.add("blocks", block);
        }
        if (properties != null) wrapped.add("properties", properties);
        return wrapped;
    }

    private static JsonElement entityMatcherPayload(JsonElement value) {
        if (value != null && value.isJsonObject() && !value.getAsJsonObject().has("type")) {
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("type", "rml:entity_match");
            for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
                wrapped.add(field.getKey(), field.getValue());
            }
            return wrapped;
        }
        return value;
    }

    private static DoubleRange range(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        return object.has(field) ? manager.decode(DoubleRange.class, object.get(field)) : null;
    }
    private static Boolean optional(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        return object.has(field) ? manager.decode(Boolean.class, object.get(field)) : null;
    }
}
