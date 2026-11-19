package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;

/** Direct-JSON predicates used before the typed decoder registry is bound. */
final class LegacyPredicates {
    private LegacyPredicates() { }

    static DataRules.Test decode(DataRules rules, JsonElement definition) {
        JsonObject object = DataRules.requireObject(definition, "predicate");
        String type = DataRules.string(object, "condition", DataRules.string(object, "type", ""));
        if (object.has("condition") && object.has("type")
                && !object.get("condition").getAsString().equals(object.get("type").getAsString()))
            throw DataRules.invalid("Predicate cannot specify different 'condition' and 'type' values");
        RuleFields.predicate(type, object);
        switch (type) {
            case "minecraft:reference": case "reference": case "rml:reference": {
                final ResourceLocation id = DataRules.resource(object, object.has("name") ? "name" : "id");
                return context -> rules.namedPredicate(id).test(context);
            }
            case "rml:all_of": case "minecraft:all_of": case "all_of": {
                final DataRules.Test[] terms = tests(rules, DataRules.required(object, "terms"));
                return context -> { for (DataRules.Test term : terms) if (!term.test(context)) return false; return true; };
            }
            case "rml:any_of": case "minecraft:any_of": case "any_of": {
                final DataRules.Test[] terms = tests(rules, DataRules.required(object, "terms"));
                return context -> { for (DataRules.Test term : terms) if (term.test(context)) return true; return false; };
            }
            case "rml:inverted": case "minecraft:inverted": case "inverted": {
                final DataRules.Test term = decode(rules, DataRules.required(object, "term"));
                return context -> !term.test(context);
            }
            case "minecraft:random_chance_with_looting": case "random_chance_with_looting": {
                final double chance = DataRules.requiredNumber(object, "chance");
                final double multiplier = DataRules.requiredNumber(object, "looting_multiplier");
                if (chance < 0 || chance > 1 || multiplier < 0) throw DataRules.invalid("Invalid random_chance_with_looting parameters");
                return context -> {
                    LootContext loot = context.getLootContext();
                    double effective = chance + (loot == null ? 0 : loot.getLootingModifier() * multiplier);
                    return context.getRandom().nextFloat() < effective;
                };
            }
            case "minecraft:random_chance": case "random_chance": case "rml:random_chance": {
                final DataRules.NumberValue chance = LegacyNumbers.decode(rules, DataRules.required(object, "chance"), false);
                return context -> {
                    double value = chance.evaluate(context);
                    if (!Double.isFinite(value) || value < 0 || value > 1) throw DataRules.invalid("chance must be within 0..1");
                    return context.getRandom().nextFloat() < value;
                };
            }
            case "minecraft:killed_by_player": case "killed_by_player": {
                final boolean inverse = DataRules.bool(object, "inverse", false);
                return context -> {
                    LootContext loot = context.getLootContext();
                    boolean playerKill = loot == null ? context.getEntity() instanceof EntityPlayer : loot.getKillerPlayer() != null;
                    return playerKill != inverse;
                };
            }
            case "rml:entity_type": case "entity_type": {
                final ResourceLocation id = DataRules.resource(object, "entity");
                return context -> context.getEntity() != null && id.equals(EntityList.getKey(context.getEntity()));
            }
            case "rml:entity_on_fire": case "entity_on_fire": {
                final boolean expected = DataRules.bool(object, "on_fire", true);
                return context -> context.getEntity() != null && context.getEntity().isBurning() == expected;
            }
            case "rml:health": case "health": {
                final DataRules.NumberValue min = LegacyNumbers.decode(rules, DataRules.optional(object, "min", 0), false);
                final DataRules.NumberValue max = LegacyNumbers.decode(rules, DataRules.optional(object, "max", Double.MAX_VALUE), false);
                return context -> context.getEntity() instanceof EntityLivingBase && inRange(((EntityLivingBase) context.getEntity()).getHealth(), min.evaluate(context), max.evaluate(context));
            }
            case "rml:level": case "level": {
                final DataRules.NumberValue min = LegacyNumbers.decode(rules, DataRules.optional(object, "min", 0), false);
                final DataRules.NumberValue max = LegacyNumbers.decode(rules, DataRules.optional(object, "max", Integer.MAX_VALUE), false);
                return context -> inRange(context.getLevel(), min.evaluate(context), max.evaluate(context));
            }
            case "rml:dimension": case "dimension": {
                final int dimension = DataRules.integer(object, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return context -> context.getWorld() != null && context.getWorld().provider.getDimension() == dimension;
            }
            case "rml:biome": case "biome": {
                final ResourceLocation biome = DataRules.resource(object, "value");
                return context -> context.getWorld() != null && context.getOrigin() != null
                        && biome.equals(context.getWorld().getBiome(context.getOrigin()).getRegistryName());
            }
            case "rml:block": case "block": {
                final ResourceLocation block = DataRules.resource(object, "value");
                final net.minecraft.block.Block expected = net.minecraft.block.Block.getBlockFromName(block.toString());
                if (expected == null) throw DataRules.invalid("Unknown block '" + block + "'");
                return context -> context.getWorld() != null && context.getOrigin() != null
                        && expected == context.getWorld().getBlockState(context.getOrigin()).getBlock();
            }
            case "rml:function_result": case "function_result": {
                final ResourceLocation function = DataRules.resource(object, "function");
                final int expected = DataRules.integer(object, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return context -> rules.invoke(function, context) == expected;
            }
            case "rml:item": case "item": {
                final ResourceLocation item = DataRules.resource(object, "value");
                final int data = DataRules.integer(object, "data", 0, 32767);
                return context -> context.getEntity() instanceof EntityLivingBase
                        && item.equals(((EntityLivingBase) context.getEntity()).getHeldItemMainhand().getItem().getRegistryName())
                        && (((EntityLivingBase) context.getEntity()).getHeldItemMainhand().getMetadata() == data);
            }
            case "rml:tag": case "tag": {
                final String registry = DataRules.string(object, "registry", "block");
                final ResourceLocation tag = DataRules.resource(object, "tag");
                final ResourceLocation id = DataRules.resource(object, "value");
                return context -> rules.tags().contains(registry, tag, id);
            }
            default: throw DataRules.invalid("Unsupported predicate type '" + type + "'");
        }
    }

    private static DataRules.Test[] tests(DataRules rules, JsonElement value) {
        if (value.isJsonArray()) {
            com.google.gson.JsonArray array = value.getAsJsonArray();
            DataRules.Test[] result = new DataRules.Test[array.size()];
            for (int index = 0; index < result.length; index++) result[index] = decode(rules, array.get(index));
            return result;
        }
        return new DataRules.Test[]{decode(rules, value)};
    }

    private static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }
}
