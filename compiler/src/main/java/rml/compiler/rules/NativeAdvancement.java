package rml.compiler.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.compiler.source.Project;

import java.util.Arrays;
import java.util.Map;

import static rml.compiler.rules.RuleJson.*;

/** Validates nested native predicates, whose deserializers otherwise ignore unknown fields. */
final class NativeAdvancement {
    private NativeAdvancement() { }

    static void validate(Project project, JsonObject conditions, SourceResource source) {
        for (Map.Entry<String, JsonElement> entry : conditions.entrySet()) {
            String field = entry.getKey();
            JsonElement value = entry.getValue();
            switch (field) {
                case "entity": case "zombie": case "villager": entity(value, source); break;
                case "item": item(value, source); break;
                case "items": for (JsonElement item : array(value, source, "items")) item(item, source); break;
                case "location": case "entered": case "exited": location(value, source); break;
                case "distance":
                    if (value.isJsonObject() && !value.getAsJsonObject().has("min") && !value.getAsJsonObject().has("max")) distance(value, source);
                    else range(value, source, "distance");
                    break;
                case "damage": damage(value, source); break;
                case "killing_blow": damageSource(value, source); break;
                case "effects": effects(value, source); break;
                case "slots":
                    JsonObject slots = object(value, source, "slots");
                    fields(slots, source, "occupied", "full", "empty");
                    for (Map.Entry<String, JsonElement> slot : slots.entrySet()) range(slot.getValue(), source, slot.getKey());
                    break;
                case "state":
                    for (Map.Entry<String, JsonElement> state : object(value, source, "block state").entrySet()) string(state.getValue(), source, "state value");
                    break;
                case "position": position(value, source); break;
                case "dimension": case "from": case "to": dimension(value, source); break;
                case "block": case "potion": case "biome": id(value, source, field); break;
                case "recipe":
                    String recipe = id(value, source, "recipe");
                    if (project.getResource("recipe", recipe) == null && !"minecraft".equals(Ids.namespace(recipe))) throw error(source, "missing_recipe", "Unknown recipe criterion: " + recipe);
                    break;
                case "feature": string(value, source, field); break;
                case "level": case "levels": case "durability": case "delta": case "duration": range(value, source, field); break;
                default: throw error(source, "unsupported_field", "Unsupported native predicate field: " + field);
            }
        }
    }

    private static void item(JsonElement value, SourceResource source) {
        JsonObject item = object(value, source, "item predicate");
        fields(item, source, "item", "data", "count", "durability", "enchantments", "potion", "nbt");
        if (item.has("item")) id(item.get("item"), source, "item predicate id");
        if (item.has("potion")) id(item.get("potion"), source, "potion");
        if (item.has("data")) integer(item.get("data"), source, "data", 0, 32767);
        if (item.has("nbt")) string(item.get("nbt"), source, "nbt");
        if (item.has("count")) range(item.get("count"), source, "count");
        if (item.has("durability")) range(item.get("durability"), source, "durability");
        if (item.has("enchantments")) for (JsonElement entry : array(item.get("enchantments"), source, "enchantments")) {
            JsonObject enchantment = object(entry, source, "enchantment predicate");
            fields(enchantment, source, "enchantment", "levels");
            if (enchantment.has("enchantment")) id(enchantment.get("enchantment"), source, "enchantment");
            if (enchantment.has("levels")) range(enchantment.get("levels"), source, "levels");
        }
    }

    private static void entity(JsonElement value, SourceResource source) {
        JsonObject entity = object(value, source, "entity predicate");
        fields(entity, source, "type", "distance", "location", "effects", "nbt");
        if (entity.has("type")) id(entity.get("type"), source, "entity type");
        if (entity.has("distance")) distance(entity.get("distance"), source);
        if (entity.has("location")) location(entity.get("location"), source);
        if (entity.has("effects")) effects(entity.get("effects"), source);
        if (entity.has("nbt")) string(entity.get("nbt"), source, "entity nbt");
    }

    private static void location(JsonElement value, SourceResource source) {
        JsonObject location = object(value, source, "location predicate");
        fields(location, source, "position", "dimension", "biome", "feature");
        if (location.has("position")) position(location.get("position"), source);
        if (location.has("dimension")) dimension(location.get("dimension"), source);
        if (location.has("biome")) id(location.get("biome"), source, "biome");
        if (location.has("feature")) string(location.get("feature"), source, "feature");
    }

    private static void position(JsonElement value, SourceResource source) {
        JsonObject position = object(value, source, "position");
        fields(position, source, "x", "y", "z");
        for (Map.Entry<String, JsonElement> axis : position.entrySet()) range(axis.getValue(), source, axis.getKey());
    }

    private static void distance(JsonElement value, SourceResource source) {
        JsonObject distance = object(value, source, "distance");
        fields(distance, source, "x", "y", "z", "absolute", "horizontal");
        for (Map.Entry<String, JsonElement> axis : distance.entrySet()) range(axis.getValue(), source, axis.getKey());
    }

    private static void dimension(JsonElement value, SourceResource source) {
        String dimension = string(value, source, "dimension");
        if (!Arrays.asList("overworld", "the_nether", "the_end").contains(dimension)) throw error(source, "unsupported_field", "1.12 dimension predicate requires overworld, the_nether, or the_end");
    }

    private static void effects(JsonElement value, SourceResource source) {
        for (Map.Entry<String, JsonElement> entry : object(value, source, "effects").entrySet()) {
            id(new JsonPrimitive(entry.getKey()), source, "effect");
            JsonObject effect = object(entry.getValue(), source, "effect predicate");
            fields(effect, source, "amplifier", "duration", "ambient", "visible");
            if (effect.has("amplifier")) range(effect.get("amplifier"), source, "amplifier");
            if (effect.has("duration")) range(effect.get("duration"), source, "duration");
            if (effect.has("ambient")) bool(effect.get("ambient"), source, "ambient");
            if (effect.has("visible")) bool(effect.get("visible"), source, "visible");
        }
    }

    private static void damage(JsonElement value, SourceResource source) {
        JsonObject damage = object(value, source, "damage predicate");
        fields(damage, source, "dealt", "taken", "blocked", "source_entity", "type");
        if (damage.has("dealt")) range(damage.get("dealt"), source, "dealt");
        if (damage.has("taken")) range(damage.get("taken"), source, "taken");
        if (damage.has("blocked")) bool(damage.get("blocked"), source, "blocked");
        if (damage.has("source_entity")) entity(damage.get("source_entity"), source);
        if (damage.has("type")) damageSource(damage.get("type"), source);
    }

    private static void damageSource(JsonElement value, SourceResource source) {
        JsonObject damage = object(value, source, "damage source predicate");
        fields(damage, source, "is_projectile", "is_explosion", "bypasses_armor", "bypasses_invulnerability", "bypasses_magic", "is_fire", "is_magic", "direct_entity", "source_entity");
        for (Map.Entry<String, JsonElement> entry : damage.entrySet()) {
            if (entry.getKey().endsWith("_entity")) entity(entry.getValue(), source);
            else bool(entry.getValue(), source, entry.getKey());
        }
    }

    private static void range(JsonElement value, SourceResource source, String field) {
        if (value != null && value.isJsonObject()) {
            JsonObject range = value.getAsJsonObject();
            fields(range, source, "min", "max");
            double min = range.has("min") ? number(range.get("min"), source, field + ".min") : -Double.MAX_VALUE;
            double max = range.has("max") ? number(range.get("max"), source, field + ".max") : Double.MAX_VALUE;
            if (min > max) throw error(source, "invalid_rule", field + " range is reversed");
        } else number(value, source, field);
    }
}
