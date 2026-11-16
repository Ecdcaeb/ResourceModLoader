package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rml.compiler.SourceResource;
import rml.compiler.source.Project;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static rml.compiler.rules.RuleJson.*;

/** Compiles predicate trees and retains named references as live runtime lookups. */
final class PredicateLowering {
    private final Project project;
    private final Map<String, JsonElement> compiled = new HashMap<>();
    private final Set<String> active = new LinkedHashSet<>();

    PredicateLowering(Project project) { this.project = project; }

    JsonElement named(String id, SourceResource caller) {
        JsonElement cached = compiled.get(id);
        if (cached != null) return copy(cached);
        SourceResource source = project.getResource("predicate", id);
        if (source == null) throw error(caller, "missing_predicate", "Unknown predicate: " + id);
        if (!active.add(id)) throw error(caller, "predicate_cycle", "Predicate reference cycle: " + active + " -> " + id);
        try {
            JsonElement result = condition(source.getJson(), source);
            compiled.put(id, result);
            return copy(result);
        } finally { active.remove(id); }
    }

    JsonArray conditions(JsonElement value, SourceResource source) {
        JsonArray result = new JsonArray();
        for (JsonElement entry : array(value, source, "conditions")) result.add(condition(entry, source));
        return result;
    }

    JsonObject condition(JsonElement value, SourceResource source) {
        if (value != null && value.isJsonArray()) {
            JsonObject result = new JsonObject();
            result.addProperty("condition", "rml:all_of");
            result.add("terms", conditions(value, source));
            return result;
        }
        JsonObject input = object(value, source, "condition");
        if (input.has("type")) {
            if (input.has("condition")) throw error(source, "invalid_rule", "Predicate node cannot contain both 'type' and 'condition'");
            id(input.get("type"), source, "predicate type");
            return expressionCondition(copy(input));
        }
        String type = type(input, "condition", source);
        JsonObject result = (JsonObject) copy(input);
        switch (type) {
            case "minecraft:reference":
                fields(input, source, "condition", "name");
                String reference = id(input.get("name"), source, "predicate name");
                named(reference, source); // Validate existence and cycles at compile time; resolve the active value at runtime.
                JsonObject live = new JsonObject();
                live.addProperty("condition", "rml:expression");
                JsonObject expression = new JsonObject();
                expression.addProperty("type", "rml:reference");
                expression.addProperty("id", reference);
                live.add("expression", expression);
                return live;
            case "minecraft:all_of":
            case "minecraft:any_of":
            case "rml:all_of":
            case "rml:any_of":
                fields(input, source, "condition", "terms");
                result.addProperty("condition", type.endsWith(":all_of") ? "rml:all_of" : "rml:any_of");
                result.add("terms", conditions(input.get("terms"), source));
                return result;
            case "minecraft:inverted":
            case "rml:inverted":
                fields(input, source, "condition", "term");
                result.addProperty("condition", "rml:inverted");
                result.add("term", condition(input.get("term"), source));
                return result;
            case "minecraft:random_chance":
                fields(input, source, "condition", "chance");
                chance(input.get("chance"), source, "chance");
                break;
            case "minecraft:random_chance_with_looting":
                fields(input, source, "condition", "chance", "looting_multiplier");
                chance(input.get("chance"), source, "chance");
                if (number(input.get("looting_multiplier"), source, "looting_multiplier") < 0) throw error(source, "invalid_rule", "looting_multiplier must be nonnegative");
                break;
            case "minecraft:killed_by_player":
                fields(input, source, "condition", "inverse");
                if (input.has("inverse")) bool(input.get("inverse"), source, "inverse");
                break;
            case "minecraft:entity_properties":
                fields(input, source, "condition", "entity", "properties");
                entity(input, source);
                JsonObject properties = object(input.get("properties"), source, "properties");
                fields(properties, source, "on_fire");
                if (properties.has("on_fire")) bool(properties.get("on_fire"), source, "on_fire");
                break;
            case "minecraft:entity_scores":
                fields(input, source, "condition", "entity", "scores");
                entity(input, source);
                JsonObject scores = object(input.get("scores"), source, "scores");
                JsonObject normalizedScores = new JsonObject();
                for (Map.Entry<String, JsonElement> score : scores.entrySet()) {
                    integerRange(score.getValue(), source, "score " + score.getKey());
                    JsonElement normalized = copy(score.getValue());
                    if (normalized.isJsonObject()) {
                        JsonObject range = normalized.getAsJsonObject();
                        if (!range.has("min")) range.addProperty("min", Integer.MIN_VALUE);
                        if (!range.has("max")) range.addProperty("max", Integer.MAX_VALUE);
                    }
                    normalizedScores.add(score.getKey(), normalized);
                }
                result.add("scores", normalizedScores);
                break;
            case "rml:expression":
                fields(input, source, "condition", "expression");
                JsonObject typed = object(input.get("expression"), source, "predicate expression");
                id(typed.get("type"), source, "predicate expression type");
                return result;
            default:
                if (!type.startsWith("minecraft:")) {
                    JsonObject extension = (JsonObject) copy(input);
                    extension.remove("condition");
                    extension.addProperty("type", type);
                    return expressionCondition(extension);
                }
                throw error(source, "unsupported_condition", "Unsupported loot condition: " + type);
        }
        result.addProperty("condition", type);
        return result;
    }

    private JsonObject expressionCondition(JsonElement expression) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("condition", "rml:expression");
        wrapper.add("expression", expression);
        return wrapper;
    }

    private void entity(JsonObject input, SourceResource source) {
        String entity = string(input.get("entity"), source, "entity");
        if (!"this".equals(entity) && !"killer".equals(entity) && !"killer_player".equals(entity)) throw error(source, "unsupported_condition", "Unsupported loot entity target: " + entity);
    }

    private void chance(JsonElement value, SourceResource source, String field) {
        double chance = number(value, source, field);
        if (chance < 0 || chance > 1) throw error(source, "invalid_rule", field + " must be in 0..1");
    }

    static void integerRange(JsonElement value, SourceResource source, String field) {
        if (value != null && value.isJsonObject()) {
            JsonObject range = value.getAsJsonObject();
            fields(range, source, "min", "max");
            if (range.entrySet().isEmpty()) throw error(source, "invalid_rule", field + " cannot have an empty range");
            int min = range.has("min") ? integer(range.get("min"), source, field + ".min", Integer.MIN_VALUE, Integer.MAX_VALUE) : Integer.MIN_VALUE;
            int max = range.has("max") ? integer(range.get("max"), source, field + ".max", Integer.MIN_VALUE, Integer.MAX_VALUE) : Integer.MAX_VALUE;
            if (min > max) throw error(source, "invalid_rule", field + " range is reversed");
        } else integer(value, source, field, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
