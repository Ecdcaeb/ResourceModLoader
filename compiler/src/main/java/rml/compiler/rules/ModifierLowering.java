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
import java.util.UUID;

import static rml.compiler.rules.RuleJson.*;

/** Lowers item modifiers while retaining named references as live runtime lookups. */
final class ModifierLowering {
    private final Project project;
    private final PredicateLowering predicates;
    private final Map<String, JsonArray> compiled = new HashMap<>();
    private final Set<String> active = new LinkedHashSet<>();

    ModifierLowering(Project project, PredicateLowering predicates) { this.project = project; this.predicates = predicates; }

    JsonArray named(String id, SourceResource caller) {
        JsonArray cached = compiled.get(id);
        if (cached != null) return (JsonArray) copy(cached);
        SourceResource source = project.getResource("item_modifier", id);
        if (source == null) throw error(caller, "missing_modifier", "Unknown item modifier: " + id);
        if (!active.add(id)) throw error(caller, "modifier_cycle", "Item modifier reference cycle: " + active + " -> " + id);
        try {
            JsonArray result = functions(source.getJson(), source);
            compiled.put(id, result);
            return (JsonArray) copy(result);
        } finally { active.remove(id); }
    }

    JsonArray functions(JsonElement value, SourceResource source) {
        JsonArray result = new JsonArray();
        if (value != null && value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) append(entry, source, result);
        } else append(value, source, result);
        return result;
    }

    private void append(JsonElement value, SourceResource source, JsonArray output) {
        JsonObject input = object(value, source, "loot function");
        String type = input.has("function") ? id(input.get("function"), source, "function")
                : type(input, "type", source);
        if (input.has("function") && input.has("type") && !type.equals(id(input.get("type"), source, "function type")))
            throw error(source, "invalid_rule", "Item modifier cannot specify different 'function' and 'type' values");
        if (!input.has("function")) input.addProperty("function", type);
        input.remove("type");
        if ("minecraft:reference".equals(type)) {
            fields(input, source, "function", "name", "conditions");
            String reference = id(input.get("name"), source, "modifier name");
            named(reference, source); // Validate existence and cycles while deferring lookup to activation time.
            JsonObject expression = new JsonObject();
            expression.addProperty("type", "rml:reference");
            expression.addProperty("id", reference);
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("function", "rml:expression");
            wrapper.add("expression", expression);
            if (input.has("conditions")) wrapper.add("conditions", predicates.conditions(input.get("conditions"), source));
            output.add(wrapper);
            return;
        }
        JsonObject result = (JsonObject) copy(input);
        switch (type) {
            case "minecraft:enchant_randomly":
                fields(input, source, "function", "conditions", "enchantments");
                if (input.has("enchantments")) {
                    JsonArray enchantments = new JsonArray();
                    for (JsonElement enchantment : array(input.get("enchantments"), source, "enchantments")) enchantments.add(new com.google.gson.JsonPrimitive(id(enchantment, source, "enchantment")));
                    result.add("enchantments", enchantments);
                }
                break;
            case "minecraft:enchant_with_levels":
                fields(input, source, "function", "conditions", "levels", "treasure");
                range(input.get("levels"), source, "levels", 0, Integer.MAX_VALUE, true);
                if (input.has("treasure")) bool(input.get("treasure"), source, "treasure");
                break;
            case "minecraft:exploration_map":
                fields(input, source, "function", "conditions", "destination", "decoration", "zoom", "search_radius", "skip_existing_chunks");
                if (input.has("destination")) string(input.get("destination"), source, "destination");
                if (input.has("decoration")) {
                    String decoration = string(input.get("decoration"), source, "decoration");
                    if (!decoration.matches("(?i)player|frame|red_marker|blue_marker|target_x|target_point|player_off_map|player_off_limits|mansion|monument")) throw error(source, "unsupported_function", "Unsupported 1.12 map decoration: " + decoration);
                }
                if (input.has("zoom")) integer(input.get("zoom"), source, "zoom", 0, 4);
                if (input.has("search_radius")) integer(input.get("search_radius"), source, "search_radius", 1, Integer.MAX_VALUE);
                if (input.has("skip_existing_chunks")) bool(input.get("skip_existing_chunks"), source, "skip_existing_chunks");
                break;
            case "minecraft:furnace_smelt":
                fields(input, source, "function", "conditions");
                break;
            case "minecraft:looting_enchant":
                fields(input, source, "function", "conditions", "count", "limit");
                range(input.get("count"), source, "count", 0, Float.MAX_VALUE, false);
                if (input.has("limit")) integer(input.get("limit"), source, "limit", 0, Integer.MAX_VALUE);
                break;
            case "minecraft:set_attributes":
                fields(input, source, "function", "conditions", "modifiers");
                attributes(input.get("modifiers"), source);
                break;
            case "minecraft:set_count":
                fields(input, source, "function", "conditions", "count");
                if (appendNumberExpression(input, type, "count", source, output)) return;
                range(input.get("count"), source, "count", 0, Integer.MAX_VALUE, true);
                break;
            case "minecraft:set_damage":
                fields(input, source, "function", "conditions", "damage");
                if (appendNumberExpression(input, type, "damage", source, output)) return;
                range(input.get("damage"), source, "damage", 0, 1, false);
                break;
            case "minecraft:set_data":
                fields(input, source, "function", "conditions", "data");
                range(input.get("data"), source, "data", 0, 32767, true);
                break;
            case "minecraft:set_components": {
                fields(input, source, "function", "conditions", "components");
                JsonObject components = object(input.get("components"), source, "components");
                if (components.entrySet().isEmpty()) throw error(source, "unsupported_function", "set_components requires at least one component");
                JsonObject item = new JsonObject();
                item.addProperty("id", "minecraft:stone");
                item.add("components", copy(components));
                JsonObject lowered = StackLowering.lower(item, source);
                if (!lowered.has("nbt") || lowered.has("data") || lowered.has("type"))
                    throw error(source, "unsupported_function", "set_components contains a component that cannot be represented by the target item NBT");
                result = new JsonObject();
                result.add("tag", copy(lowered.get("nbt")));
                type = "minecraft:set_nbt";
                break;
            }
            case "minecraft:set_nbt":
                fields(input, source, "function", "conditions", "tag");
                String tag = string(input.get("tag"), source, "tag").trim();
                if (!tag.startsWith("{") || !tag.endsWith("}")) throw error(source, "invalid_rule", "set_nbt tag must be a compound SNBT string");
                break;
            case "minecraft:set_name":
                fields(input, source, "function", "conditions", "name");
                JsonObject display = new JsonObject();
                display.addProperty("Name", StackLowering.text(input.get("name"), source));
                result = nbtFunction(display, input, source);
                type = "minecraft:set_nbt";
                break;
            case "minecraft:set_lore":
                fields(input, source, "function", "conditions", "lore", "replace");
                if (!input.has("replace") || !bool(input.get("replace"), source, "replace")) throw error(source, "unsupported_function", "set_lore requires replace:true; the target cannot append lore");
                JsonArray lore = new JsonArray();
                for (JsonElement line : array(input.get("lore"), source, "lore")) lore.add(new com.google.gson.JsonPrimitive(StackLowering.text(line, source)));
                JsonObject loreDisplay = new JsonObject();
                loreDisplay.add("Lore", lore);
                result = nbtFunction(loreDisplay, input, source);
                type = "minecraft:set_nbt";
                break;
            case "rml:expression":
                fields(input, source, "function", "conditions", "expression");
                output.add((JsonObject) copy(input));
                return;
            default:
                if (!type.startsWith("minecraft:")) {
                    JsonObject expression = (JsonObject) copy(input);
                    expression.remove("function");
                    expression.remove("conditions");
                    expression.addProperty("type", type);
                    output.add(expressionFunction(expression, input, source));
                    return;
                }
                throw error(source, "unsupported_function", "Unsupported item modifier function: " + type);
        }
        result.addProperty("function", type);
        if (input.has("conditions")) result.add("conditions", predicates.conditions(input.get("conditions"), source));
        output.add(result);
    }

    private JsonObject expressionFunction(JsonObject expression, JsonObject input, SourceResource source) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("function", "rml:expression");
        wrapper.add("expression", expression);
        if (input.has("conditions")) wrapper.add("conditions", predicates.conditions(input.get("conditions"), source));
        return wrapper;
    }

    private boolean appendNumberExpression(JsonObject input, String type, String field,
                                           SourceResource source, JsonArray output) {
        JsonElement value = input.get(field);
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().has("type")) return false;
        JsonObject expression = (JsonObject) copy(input);
        expression.remove("function");
        expression.remove("conditions");
        expression.addProperty("type", type);
        JsonObject number = expression.getAsJsonObject(field);
        number.addProperty("type", id(number.get("type"), source, "number provider type"));
        output.add(expressionFunction(expression, input, source));
        return true;
    }

    private JsonObject nbtFunction(JsonObject display, JsonObject input, SourceResource source) {
        JsonObject tag = new JsonObject();
        tag.add("display", display);
        JsonObject result = new JsonObject();
        result.addProperty("tag", snbt(tag, source));
        return result;
    }

    private void attributes(JsonElement value, SourceResource source) {
        for (JsonElement entry : array(value, source, "modifiers")) {
            JsonObject modifier = object(entry, source, "attribute modifier");
            fields(modifier, source, "name", "attribute", "operation", "amount", "slot", "id");
            string(modifier.get("name"), source, "attribute modifier name");
            String attribute = string(modifier.get("attribute"), source, "attribute");
            if (attribute.contains(":")) throw error(source, "unsupported_function", "1.12 attributes use names such as generic.attackDamage");
            String operation = string(modifier.get("operation"), source, "operation");
            if (!"addition".equals(operation) && !"multiply_base".equals(operation) && !"multiply_total".equals(operation)) throw error(source, "invalid_rule", "Invalid attribute operation: " + operation);
            range(modifier.get("amount"), source, "amount", -Float.MAX_VALUE, Float.MAX_VALUE, false);
            JsonElement slots = modifier.get("slot");
            if (slots != null && slots.isJsonArray()) {
                if (slots.getAsJsonArray().size() == 0) throw error(source, "invalid_rule", "Attribute slot list cannot be empty");
                for (JsonElement slot : slots.getAsJsonArray()) slot(slot, source);
            } else slot(slots, source);
            if (modifier.has("id")) {
                try { UUID.fromString(string(modifier.get("id"), source, "attribute UUID")); }
                catch (IllegalArgumentException e) { throw error(source, "invalid_rule", "Attribute id must be a UUID"); }
            }
        }
    }

    private void slot(JsonElement value, SourceResource source) {
        String slot = string(value, source, "slot");
        if (!"mainhand".equals(slot) && !"offhand".equals(slot) && !"feet".equals(slot) && !"legs".equals(slot) && !"chest".equals(slot) && !"head".equals(slot)) throw error(source, "invalid_rule", "Unknown equipment slot: " + slot);
    }

    static void range(JsonElement value, SourceResource source, String field, double minimum, double maximum, boolean integral) {
        if (value != null && value.isJsonObject()) {
            JsonObject range = value.getAsJsonObject();
            fields(range, source, "min", "max");
            double min = bounded(range.get("min"), source, field + ".min", minimum, maximum, integral);
            double max = bounded(range.get("max"), source, field + ".max", minimum, maximum, integral);
            if (min > max) throw error(source, "invalid_rule", field + " range is reversed");
        } else bounded(value, source, field, minimum, maximum, integral);
    }

    private static double bounded(JsonElement value, SourceResource source, String field, double minimum, double maximum, boolean integral) {
        double result = number(value, source, field);
        if (result < minimum || result > maximum || integral && result != Math.rint(result)) throw error(source, "invalid_rule", field + " is outside its supported range");
        return result;
    }
}
