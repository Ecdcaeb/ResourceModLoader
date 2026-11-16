package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
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

/** Context predicates reusable by loot, enchantments, timelines and user-registered nodes. */
final class ContextPredicateDecoders {
    private ContextPredicateDecoders() { }
    @FunctionalInterface private interface Comparison { boolean test(double left, double right); }
    static void register(DeserializerManager manager) {
        for (String name : new String[]{"value_check", "compare", "time_check", "weather_check", "light_check", "match_tool",
                "match_entity", "match_block", "distance", "damage_source", "score_check", "survives_explosion", "table_bonus"}) {
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
                if (!("this".equals(target) || "attacker".equals(target) || "direct_attacker".equals(target) || "killer_player".equals(target))) throw error(object, "Unknown entity context target " + target);
                return context -> {
                    Entity entity;
                    if ("this".equals(target)) entity = context.getEntity();
                    else if ("killer_player".equals(target)) {
                        Entity source = context.getLootContext() != null ? context.getLootContext().getKillerPlayer()
                                : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
                        entity = source instanceof EntityPlayer ? source : null;
                    }
                    else {
                        DamageSource damage = context.getDamageSource();
                        entity = damage == null ? null : "direct_attacker".equals(target) ? damage.getImmediateSource() : damage.getTrueSource();
                    }
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
                boolean self = "this".equals(holder);
                return context -> {
                    if (context.getWorld() == null || self && context.getEntity() == null) return false;
                    Entity entity = context.getEntity();
                    String name = !self ? holder : entity instanceof EntityPlayer ? entity.getName() : entity.getCachedUniqueIdString();
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
            default: throw error(object, "Unknown context predicate " + type);
        }
    }

    private static BlockPos offset(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        return object.has("offset") ? manager.decode(BlockPos.class, object.get("offset")) : BlockPos.ORIGIN;
    }
    private static DoubleRange range(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        return object.has(field) ? manager.decode(DoubleRange.class, object.get(field)) : null;
    }
    private static Boolean optional(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        return object.has(field) ? manager.decode(Boolean.class, object.get(field)) : null;
    }
    private static JsonElement required(JsonObject object, String field) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) throw error(object, "Missing predicate field " + field);
        return value;
    }
    private static void fields(JsonObject object, String... allowed) throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if ("type".equals(field.getKey())) continue;
            boolean present = false;
            for (String name : allowed) if (name.equals(field.getKey())) { present = true; break; }
            if (!present) throw error(object, "Unsupported predicate field " + field.getKey());
        }
    }
    private static JsonDeserializeException error(JsonElement value, String message) { return new JsonDeserializeException(value, message); }
}
