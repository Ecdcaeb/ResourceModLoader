package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;

/** Direct-JSON numeric providers used before the typed decoder registry is bound. */
final class LegacyNumbers {
    private LegacyNumbers() { }

    static DataRules.NumberValue decode(DataRules rules, JsonElement definition, boolean integerOnly) {
        if (definition == null || definition.isJsonNull()) throw DataRules.invalid("Missing numeric definition");
        if (definition.isJsonPrimitive() && definition.getAsJsonPrimitive().isNumber()) {
            final double constant = DataRules.checkedNumber(definition.getAsDouble(), integerOnly);
            return context -> constant;
        }
        JsonObject object = DataRules.requireObject(definition, "numeric definition");
        String type = DataRules.string(object, "type", "constant");
        switch (type) {
            case "constant": case "minecraft:constant": {
                RuleFields.only(object, "type", "value");
                final double value = DataRules.checkedNumber(DataRules.requiredNumber(object, "value"), integerOnly);
                return context -> value;
            }
            case "uniform": case "minecraft:uniform": {
                RuleFields.only(object, "type", "value", "min", "max");
                JsonObject bounds = object.has("value") ? DataRules.requireObject(object.get("value"), "uniform value") : object;
                if (object.has("value")) RuleFields.only(bounds, "min", "max");
                final DataRules.NumberValue min = decode(rules, DataRules.required(bounds, "min"), integerOnly);
                final DataRules.NumberValue max = decode(rules, DataRules.required(bounds, "max"), integerOnly);
                return integerOnly
                        ? context -> DataRules.uniformInt(min.evaluate(context), max.evaluate(context), context)
                        : context -> { double low = min.evaluate(context), high = max.evaluate(context); return low >= high ? low : low + context.getRandom().nextDouble() * (high - low); };
            }
            case "binomial": case "minecraft:binomial": {
                RuleFields.only(object, "type", "n", "p");
                final int n = DataRules.integer(object, "n", 0, 1000000);
                final double p = DataRules.requiredNumber(object, "p");
                if (p < 0 || p > 1) throw DataRules.invalid("binomial p must be within 0..1");
                return context -> { int result = 0; for (int i = 0; i < n; i++) if (context.getRandom().nextDouble() < p) result++; return result; };
            }
            case "level": case "minecraft:level": case "rml:context_level":
                RuleFields.only(object, "type");
                return DataContext::getLevel;
            case "context_int_provider": case "minecraft:context_int_provider":
            case "context_float_provider": case "minecraft:context_float_provider": case "rml:reference": {
                RuleFields.only(object, "type", "id");
                final ResourceLocation id = DataRules.resource(object, "id");
                final boolean isInt = "context_int_provider".equals(type) || "minecraft:context_int_provider".equals(type);
                return context -> rules.activeNumber(isInt, id).evaluate(context);
            }
            case "score": case "minecraft:score": {
                RuleFields.only(object, "type", "score", "target");
                final String objectiveName = DataRules.string(object, "score", "");
                final String targetName = DataRules.string(object, "target", "this");
                if (objectiveName.isEmpty()) throw DataRules.invalid("Score provider requires a score objective name");
                final LootContext.EntityTarget target;
                try { target = LootContext.EntityTarget.fromString(targetName); }
                catch (RuntimeException e) { throw new IllegalArgumentException("Unsupported score target '" + targetName + "'", e); }
                return context -> {
                    Entity entity = context.getLootContext() == null ? context.getEntity() : context.getLootContext().getEntity(target);
                    WorldServer world = context.getWorld() != null ? context.getWorld() : context.getLootContext() == null ? null : context.getLootContext().getWorld();
                    if (entity == null || world == null) return 0;
                    net.minecraft.scoreboard.Scoreboard scoreboard = world.getScoreboard();
                    net.minecraft.scoreboard.ScoreObjective objective = scoreboard.getObjective(objectiveName);
                    if (objective == null || !scoreboard.entityHasObjective(entity.getName(), objective)) return 0;
                    return scoreboard.getOrCreateScore(entity.getName(), objective).getScorePoints();
                };
            }
            default: throw DataRules.invalid("Unsupported numeric provider type '" + type + "'");
        }
    }
}
