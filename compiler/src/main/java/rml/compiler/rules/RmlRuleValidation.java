package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rml.compiler.SourceResource;

import static rml.compiler.rules.RuleJson.error;
import static rml.compiler.rules.RuleJson.fields;
import static rml.compiler.rules.RuleJson.object;

/** Compile-time field contract for RML-native rule nodes; other rml types stay open for extensions. */
final class RmlRuleValidation {
    private static final String[] MODIFIER_BASE = {"function", "conditions"};
    private static final String[] PREDICATE_BASE = {"condition", "type"};

    private RmlRuleValidation() { }

    static void modifier(String type, JsonObject input, SourceResource source) {
        switch (type) {
            case "rml:furnace_smelt":
                fields(input, source, MODIFIER_BASE);
                return;
            case "rml:set_data":
                fields(input, source, merge(MODIFIER_BASE, "data"));
                required(input, source, "data");
                return;
            case "rml:set_repair_cost":
                fields(input, source, merge(MODIFIER_BASE, "cost"));
                required(input, source, "cost");
                return;
            case "rml:set_unbreakable":
                fields(input, source, merge(MODIFIER_BASE, "value"));
                if (input.has("value")) RuleJson.bool(input.get("value"), source, "value");
                return;
            case "rml:enchant_randomly":
                fields(input, source, merge(MODIFIER_BASE, "enchantments"));
                if (input.has("enchantments")) {
                    for (JsonElement enchantment : RuleJson.array(input.get("enchantments"), source, "enchantments")) {
                        RuleJson.id(enchantment, source, "enchantments");
                    }
                }
                return;
            case "rml:enchant_with_levels":
                fields(input, source, merge(MODIFIER_BASE, "levels", "treasure"));
                required(input, source, "levels");
                if (input.has("treasure")) RuleJson.bool(input.get("treasure"), source, "treasure");
                return;
            case "rml:looting_enchant":
                fields(input, source, merge(MODIFIER_BASE, "count", "limit"));
                required(input, source, "count");
                if (input.has("limit")) RuleJson.integer(input.get("limit"), source, "limit", 0, Integer.MAX_VALUE);
                return;
            case "rml:copy_name":
                fields(input, source, merge(MODIFIER_BASE, "source"));
                choice(RuleJson.string(required(input, source, "source"), source, "source"), source, "source",
                        "this", "killer", "killer_player", "block_entity");
                return;
            case "rml:fill_player_head":
                fields(input, source, merge(MODIFIER_BASE, "entity"));
                choice(RuleJson.string(required(input, source, "entity"), source, "entity"), source, "entity",
                        "this", "killer", "killer_player");
                return;
            case "rml:set_contents":
                fields(input, source, merge(MODIFIER_BASE, "items", "block_entity"));
                RuleJson.array(required(input, source, "items"), source, "items");
                if (input.has("block_entity")) RuleJson.id(input.get("block_entity"), source, "block_entity");
                return;
            case "rml:set_loot_table":
                fields(input, source, merge(MODIFIER_BASE, "name", "seed"));
                RuleJson.id(required(input, source, "name"), source, "name");
                if (input.has("seed")) RuleJson.number(input.get("seed"), source, "seed");
                return;
            case "rml:set_attributes":
                fields(input, source, merge(MODIFIER_BASE, "modifiers"));
                JsonArray modifiers = RuleJson.array(required(input, source, "modifiers"), source, "modifiers");
                if (modifiers.size() == 0) throw error(source, "invalid_rule", "modifiers must not be empty");
                for (JsonElement element : modifiers) {
                    JsonObject modifier = object(element, source, "modifiers entry");
                    fields(modifier, source, "attribute", "name", "amount", "operation", "slot", "uuid");
                    String attribute = RuleJson.string(required(modifier, source, "attribute"), source, "attribute");
                    if (attribute.isEmpty()) throw error(source, "invalid_rule", "attribute must not be empty");
                    RuleJson.string(required(modifier, source, "name"), source, "name");
                    RuleJson.number(required(modifier, source, "amount"), source, "amount");
                    choice(RuleJson.string(required(modifier, source, "operation"), source, "operation"), source, "operation",
                            "addition", "multiply_base", "multiply_total");
                    if (modifier.has("slot")) {
                        choice(RuleJson.string(modifier.get("slot"), source, "slot"), source, "slot",
                                "mainhand", "offhand", "head", "chest", "legs", "feet");
                    }
                    if (modifier.has("uuid")) RuleJson.string(modifier.get("uuid"), source, "uuid");
                }
                return;
            default:
                // Unknown rml nodes remain extension points; runtime decoding reports their contract.
        }
    }

    static void predicate(String type, JsonObject input, SourceResource source) {
        switch (type) {
            case "rml:held_item":
                fields(input, source, merge(PREDICATE_BASE, "predicate", "hand"));
                RuleJson.object(required(input, source, "predicate"), source, "predicate");
                if (input.has("hand")) {
                    choice(RuleJson.string(input.get("hand"), source, "hand"), source, "hand", "mainhand", "offhand");
                }
                return;
            case "rml:enchantments":
                fields(input, source, merge(PREDICATE_BASE, "enchantments"));
                RuleJson.object(required(input, source, "enchantments"), source, "enchantments");
                return;
            case "rml:entity_properties":
                fields(input, source, merge(PREDICATE_BASE, "entity", "predicate", "flags"));
                if (input.has("entity")) {
                    choice(RuleJson.string(input.get("entity"), source, "entity"), source, "entity",
                            "this", "killer", "killer_player", "attacker", "direct_attacker");
                }
                RuleJson.object(required(input, source, "predicate"), source, "predicate");
                if (input.has("flags")) RuleJson.object(input.get("flags"), source, "flags");
                return;
            case "rml:entity_scores":
                fields(input, source, merge(PREDICATE_BASE, "entity", "scores"));
                RuleJson.string(required(input, source, "entity"), source, "entity");
                RuleJson.object(required(input, source, "scores"), source, "scores");
                return;
            case "rml:block_state_property":
                fields(input, source, merge(PREDICATE_BASE, "block", "properties", "offset"));
                RuleJson.id(required(input, source, "block"), source, "block");
                if (input.has("properties")) RuleJson.object(input.get("properties"), source, "properties");
                return;
            case "rml:location_check":
                fields(input, source, merge(PREDICATE_BASE, "offset", "block", "light", "biome", "fluid", "position"));
                if (input.has("biome")) RuleJson.id(input.get("biome"), source, "biome");
                if (input.has("fluid")) {
                    choice(RuleJson.string(input.get("fluid"), source, "fluid"), source, "fluid", "water", "lava");
                }
                if (input.has("position")) RuleJson.object(input.get("position"), source, "position");
                return;
            case "rml:enchantment_level":
                fields(input, source, merge(PREDICATE_BASE, "enchantment", "level"));
                RuleJson.id(required(input, source, "enchantment"), source, "enchantment");
                required(input, source, "level");
                return;
            default:
                // Unknown rml nodes remain extension points; runtime decoding reports their contract.
        }
    }

    private static JsonElement required(JsonObject object, SourceResource source, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) throw error(source, "invalid_rule", "Missing required field: " + field);
        return value;
    }

    private static void choice(String value, SourceResource source, String field, String... allowed) {
        for (String candidate : allowed) if (candidate.equals(value)) return;
        throw error(source, "invalid_rule", field + " must be one of " + String.join(", ", allowed));
    }

    private static String[] merge(String[] base, String... extra) {
        String[] result = new String[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }
}
