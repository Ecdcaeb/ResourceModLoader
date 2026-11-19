package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Field contract for the legacy direct-JSON rules kept beside the typed decoder registry. */
final class RuleFields {
    private RuleFields() { }

    static void only(JsonObject object, String... allowed) {
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            boolean found = false;
            for (String candidate : allowed) {
                if (candidate.equals(field.getKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new IllegalArgumentException("Unsupported rule field '" + field.getKey() + "'");
        }
    }

    static void modifier(String type, JsonObject object) {
        switch (type) {
            case "rml:sequence": only(object, "type", "functions"); return;
            case "rml:reference": case "reference":
                only(object, "type", "function", "name", "id", "conditions"); return;
            case "minecraft:set_count": case "rml:set_count": case "set_count":
                only(object, "type", "function", "count", "conditions"); return;
            case "minecraft:set_damage": case "rml:set_damage": case "set_damage":
                only(object, "type", "function", "damage", "conditions"); return;
            case "minecraft:set_nbt": case "rml:set_nbt": case "set_nbt":
                only(object, "type", "function", "tag", "conditions"); return;
            case "minecraft:set_components":
                only(object, "type", "function", "components", "conditions"); return;
            default: return;
        }
    }

    static void predicate(String type, JsonObject object) {
        switch (type) {
            case "minecraft:reference": case "reference": case "rml:reference":
                only(object, "condition", "type", "name", "id"); return;
            case "rml:all_of": case "minecraft:all_of": case "all_of":
            case "rml:any_of": case "minecraft:any_of": case "any_of":
                only(object, "condition", "type", "terms"); return;
            case "rml:inverted": case "minecraft:inverted": case "inverted":
                only(object, "condition", "type", "term"); return;
            case "minecraft:random_chance": case "random_chance": case "rml:random_chance":
                only(object, "condition", "type", "chance"); return;
            case "minecraft:random_chance_with_looting": case "random_chance_with_looting":
                only(object, "condition", "type", "chance", "looting_multiplier"); return;
            case "minecraft:killed_by_player": case "killed_by_player":
                only(object, "condition", "type", "inverse"); return;
            case "rml:entity_type": case "entity_type":
                only(object, "condition", "type", "entity"); return;
            case "rml:entity_on_fire": case "entity_on_fire":
                only(object, "condition", "type", "on_fire"); return;
            case "rml:health": case "health": case "rml:level": case "level":
                only(object, "condition", "type", "min", "max"); return;
            case "rml:dimension": case "dimension": case "rml:biome": case "biome":
            case "rml:block": case "block":
                only(object, "condition", "type", "value"); return;
            case "rml:function_result": case "function_result":
                only(object, "condition", "type", "function", "value"); return;
            case "rml:item": case "item":
                only(object, "condition", "type", "value", "data"); return;
            case "rml:tag": case "tag":
                only(object, "condition", "type", "registry", "tag", "value"); return;
            default: return;
        }
    }

    static void effect(String type, JsonObject object) {
        switch (type) {
            case "rml:noop": only(object, "type"); return;
            case "rml:run_function": only(object, "type", "function"); return;
            case "rml:damage": case "rml:heal": only(object, "type", "amount"); return;
            case "rml:sequence": only(object, "type", "effects"); return;
            case "rml:conditional": only(object, "type", "condition", "on_true", "on_false"); return;
            case "rml:weighted": only(object, "type", "distribution"); return;
            default: return;
        }
    }
}
