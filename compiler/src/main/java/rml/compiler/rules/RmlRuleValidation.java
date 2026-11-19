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
            case "rml:identity":
                fields(input, source, MODIFIER_BASE);
                return;
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
            case "rml:conditional":
                fields(input, source, merge(MODIFIER_BASE, "condition", "on_true", "on_false"));
                RuleJson.object(required(input, source, "condition"), source, "condition");
                RuleJson.object(required(input, source, "on_true"), source, "on_true");
                if (input.has("on_false")) RuleJson.object(input.get("on_false"), source, "on_false");
                return;
            case "rml:weighted": {
                fields(input, source, merge(MODIFIER_BASE, "distribution"));
                JsonElement distribution = required(input, source, "distribution");
                if (!distribution.isJsonArray()) RuleJson.object(distribution, source, "distribution");
                return;
            }
            case "rml:limit_count":
                fields(input, source, merge(MODIFIER_BASE, "limit"));
                required(input, source, "limit");
                return;
            case "rml:set_enchantments":
                fields(input, source, merge(MODIFIER_BASE, "enchantments", "add"));
                RuleJson.object(required(input, source, "enchantments"), source, "enchantments");
                if (input.has("add")) RuleJson.bool(input.get("add"), source, "add");
                return;
            case "rml:apply_bonus":
                fields(input, source, merge(MODIFIER_BASE, "enchantment", "formula", "bonus", "probability"));
                RuleJson.id(required(input, source, "enchantment"), source, "enchantment");
                choice(RuleJson.string(required(input, source, "formula"), source, "formula"), source, "formula",
                        "ore_drops", "uniform", "binomial");
                if (input.has("bonus")) required(input, source, "bonus");
                if (input.has("probability")) required(input, source, "probability");
                return;
            case "rml:set_name":
                fields(input, source, merge(MODIFIER_BASE, "name"));
                required(input, source, "name");
                return;
            case "rml:set_lore":
                fields(input, source, merge(MODIFIER_BASE, "lore", "replace"));
                RuleJson.array(required(input, source, "lore"), source, "lore");
                if (input.has("replace")) RuleJson.bool(input.get("replace"), source, "replace");
                return;
            case "rml:copy_nbt":
                fields(input, source, merge(MODIFIER_BASE, "source", "path", "target", "storage", "operation"));
                choice(RuleJson.string(required(input, source, "source"), source, "source"), source, "source",
                        "entity", "block_entity", "tool", "inputs", "storage");
                RuleJson.string(required(input, source, "path"), source, "path");
                RuleJson.string(required(input, source, "target"), source, "target");
                if (input.has("storage")) RuleJson.id(input.get("storage"), source, "storage");
                if (input.has("operation")) {
                    choice(RuleJson.string(input.get("operation"), source, "operation"), source, "operation",
                            "replace", "merge", "append");
                }
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
            case "rml:all_of": case "rml:any_of":
                fields(input, source, merge(PREDICATE_BASE, "terms"));
                RuleJson.array(required(input, source, "terms"), source, "terms");
                return;
            case "rml:inverted":
                fields(input, source, merge(PREDICATE_BASE, "term"));
                RuleJson.object(required(input, source, "term"), source, "term");
                return;
            case "rml:random_chance":
                fields(input, source, merge(PREDICATE_BASE, "chance"));
                required(input, source, "chance");
                return;
            case "rml:entity_type":
                fields(input, source, merge(PREDICATE_BASE, "entity"));
                RuleJson.id(required(input, source, "entity"), source, "entity");
                return;
            case "rml:entity_on_fire":
                fields(input, source, merge(PREDICATE_BASE, "on_fire"));
                if (input.has("on_fire")) RuleJson.bool(input.get("on_fire"), source, "on_fire");
                return;
            case "rml:health": case "rml:level":
                fields(input, source, merge(PREDICATE_BASE, "min", "max"));
                return;
            case "rml:dimension":
                fields(input, source, merge(PREDICATE_BASE, "value"));
                RuleJson.integer(required(input, source, "value"), source, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return;
            case "rml:biome": case "rml:block":
                fields(input, source, merge(PREDICATE_BASE, "value"));
                RuleJson.id(required(input, source, "value"), source, "value");
                return;
            case "rml:function_result":
                fields(input, source, merge(PREDICATE_BASE, "function", "value"));
                RuleJson.id(required(input, source, "function"), source, "function");
                RuleJson.integer(required(input, source, "value"), source, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return;
            case "rml:item":
                fields(input, source, merge(PREDICATE_BASE, "value", "data"));
                RuleJson.id(required(input, source, "value"), source, "value");
                if (input.has("data")) RuleJson.integer(input.get("data"), source, "data", 0, 32767);
                return;
            case "rml:tag":
                fields(input, source, merge(PREDICATE_BASE, "registry", "tag", "value"));
                RuleJson.string(required(input, source, "registry"), source, "registry");
                RuleJson.id(required(input, source, "tag"), source, "tag");
                RuleJson.id(required(input, source, "value"), source, "value");
                return;
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
            case "rml:always": case "rml:never":
                fields(input, source, PREDICATE_BASE);
                return;
            case "rml:entity_team":
                fields(input, source, merge(PREDICATE_BASE, "team"));
                RuleJson.string(required(input, source, "team"), source, "team");
                return;
            case "rml:entity_riding":
                fields(input, source, merge(PREDICATE_BASE, "vehicle"));
                if (input.has("vehicle")) RuleJson.id(input.get("vehicle"), source, "vehicle");
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
