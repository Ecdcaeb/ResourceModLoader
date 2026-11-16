package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import rml.compiler.SourceResource;

import java.util.Map;

import static rml.compiler.rules.RuleJson.*;

/** Converts modern stack results into the actual RML 1.12 stack deserializer schema. */
final class StackLowering {
    private StackLowering() { }

    static JsonObject lower(JsonElement value, SourceResource source) {
        if (value != null && value.isJsonPrimitive()) {
            JsonObject result = new JsonObject();
            result.addProperty("item", id(value, source, "item"));
            return result;
        }
        JsonObject input = object(value, source, "item stack");
        fields(input, source, "id", "item", "count", "data", "nbt", "components");
        if (input.has("id") && input.has("item")) throw error(source, "invalid_rule", "A stack cannot contain both id and item");
        JsonObject result = new JsonObject();
        result.addProperty("item", id(input.get(input.has("id") ? "id" : "item"), source, "item"));
        if (input.has("count")) result.addProperty("count", integer(input.get("count"), source, "count", 1, 64));
        if (input.has("data")) result.addProperty("data", integer(input.get("data"), source, "data", 0, 32767));
        JsonObject nbt = new JsonObject();
        String rawNbt = null;
        if (input.has("nbt")) {
            JsonElement valueNbt = input.get("nbt");
            if (valueNbt.isJsonObject()) nbt = (JsonObject) copy(valueNbt);
            else rawNbt = compoundString(valueNbt, source, "nbt");
        }
        JsonArray enchantments = new JsonArray();
        if (input.has("components")) {
            JsonObject components = object(input.get("components"), source, "components");
            for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
                String key = id(new JsonPrimitive(entry.getKey()), source, "component");
                JsonElement component = entry.getValue();
                if ("minecraft:custom_data".equals(key)) {
                    if (component.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> tag : component.getAsJsonObject().entrySet()) {
                            put(nbt, tag.getKey(), copy(tag.getValue()), source);
                        }
                    } else {
                        if (rawNbt != null) throw error(source, "unsupported_component", "Cannot merge two raw SNBT compounds");
                        rawNbt = compoundString(component, source, key);
                    }
                } else if ("minecraft:custom_name".equals(key)) {
                    put(display(nbt, source), "Name", new JsonPrimitive(text(component, source)), source);
                } else if ("minecraft:lore".equals(key)) {
                    JsonArray lore = new JsonArray();
                    for (JsonElement line : array(component, source, "lore")) lore.add(new JsonPrimitive(text(line, source)));
                    put(display(nbt, source), "Lore", lore, source);
                } else if ("minecraft:damage".equals(key)) {
                    if (result.has("data")) throw error(source, "invalid_rule", "damage and data both specify item damage");
                    result.addProperty("data", integer(component, source, key, 0, 32767));
                } else if ("minecraft:provides_trim_material".equals(key)) {
                    if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isString())
                        throw error(source, "unsupported_component", "provides_trim_material must be a resource location string");
                    put(nbt, "rml_trim_material", new JsonPrimitive(id(component, source, key)), source);
                } else if ("minecraft:unbreakable".equals(key)) {
                    boolean enabled;
                    if (component.isJsonObject()) {
                        JsonObject options = component.getAsJsonObject();
                        fields(options, source, "show_in_tooltip");
                        if (options.has("show_in_tooltip") && !bool(options.get("show_in_tooltip"), source, "show_in_tooltip")) hide(nbt, 4, source);
                        enabled = true;
                    } else enabled = bool(component, source, key);
                    put(nbt, "Unbreakable", new JsonPrimitive(enabled), source);
                } else if ("minecraft:repair_cost".equals(key)) {
                    put(nbt, "RepairCost", new JsonPrimitive(integer(component, source, key, 0, Integer.MAX_VALUE)), source);
                } else if ("minecraft:can_break".equals(key) || "minecraft:can_place_on".equals(key)) {
                    JsonArray blocks = adventureBlocks(component, nbt, source, key);
                    put(nbt, "minecraft:can_break".equals(key) ? "CanDestroy" : "CanPlaceOn", blocks, source);
                } else if ("minecraft:enchantments".equals(key)) {
                    JsonObject levels = object(component, source, key);
                    if (levels.has("levels")) {
                        fields(levels, source, "levels", "show_in_tooltip");
                        if (levels.has("show_in_tooltip") && !bool(levels.get("show_in_tooltip"), source, "show_in_tooltip")) hide(nbt, 1, source);
                        levels = object(levels.get("levels"), source, "enchantment levels");
                    }
                    for (Map.Entry<String, JsonElement> enchantment : levels.entrySet()) {
                        JsonObject lowered = new JsonObject();
                        lowered.addProperty("name", id(new JsonPrimitive(enchantment.getKey()), source, "enchantment"));
                        lowered.addProperty("level", integer(enchantment.getValue(), source, "enchantment level", 1, 32767));
                        enchantments.add(lowered);
                    }
                } else throw error(source, "unsupported_component", "Unsupported item component: " + key);
            }
        }
        if (rawNbt != null && nbt.entrySet().size() != 0) {
            throw error(source, "unsupported_component", "Use a JSON custom_data/nbt object to combine NBT with other components");
        }
        if (rawNbt != null) result.addProperty("nbt", rawNbt);
        else if (!nbt.entrySet().isEmpty()) result.addProperty("nbt", snbt(nbt, source));
        if (enchantments.size() == 0) return result;
        JsonObject wrapped = new JsonObject();
        wrapped.addProperty("type", "rml:enchantmented_item");
        wrapped.add("item", result);
        wrapped.add("enchantment", enchantments);
        return wrapped;
    }

    private static JsonArray adventureBlocks(JsonElement value, JsonObject nbt, SourceResource source, String key) {
        JsonArray result = new JsonArray();
        if (value.isJsonArray()) {
            for (JsonElement block : value.getAsJsonArray()) result.add(new JsonPrimitive(id(block, source, key)));
            return result;
        }
        JsonObject options = object(value, source, key);
        fields(options, source, "predicates", "show_in_tooltip");
        if (options.has("show_in_tooltip") && !bool(options.get("show_in_tooltip"), source, "show_in_tooltip")) {
            hide(nbt, "minecraft:can_break".equals(key) ? 8 : 16, source);
        }
        for (JsonElement valuePredicate : array(options.get("predicates"), source, "block predicates")) {
            JsonObject predicate = object(valuePredicate, source, "block predicate");
            fields(predicate, source, "blocks");
            JsonElement blocks = predicate.get("blocks");
            if (blocks != null && blocks.isJsonArray()) {
                for (JsonElement block : blocks.getAsJsonArray()) result.add(new JsonPrimitive(id(block, source, "block")));
            } else result.add(new JsonPrimitive(id(blocks, source, "block")));
        }
        return result;
    }

    private static String compoundString(JsonElement value, SourceResource source, String field) {
        String text = string(value, source, field).trim();
        if (!text.startsWith("{") || !text.endsWith("}")) throw error(source, "invalid_rule", field + " must be a compound SNBT string");
        return text;
    }

    private static void put(JsonObject target, String field, JsonElement value, SourceResource source) {
        if (target.has(field)) throw error(source, "invalid_rule", "Conflicting NBT/component field: " + field);
        target.add(field, value);
    }

    private static JsonObject display(JsonObject nbt, SourceResource source) {
        if (!nbt.has("display")) nbt.add("display", new JsonObject());
        return object(nbt.get("display"), source, "display NBT");
    }

    private static void hide(JsonObject nbt, int bit, SourceResource source) {
        int old = nbt.has("HideFlags") ? integer(nbt.get("HideFlags"), source, "HideFlags", 0, 255) : 0;
        nbt.addProperty("HideFlags", old | bit);
    }

    static String text(JsonElement value, SourceResource source) {
        if (value == null) throw error(source, "invalid_rule", "Missing text component");
        if (value.isJsonPrimitive()) {
            String plain = string(value, source, "text");
            if (plain.startsWith("{") || plain.startsWith("[")) {
                try { return text(new JsonParser().parse(plain), source); }
                catch (com.google.gson.JsonParseException e) { throw error(source, "invalid_rule", "Malformed serialized text component"); }
            }
            return plain;
        }
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonElement part : value.getAsJsonArray()) result.append(text(part, source));
            return result.toString();
        }
        JsonObject component = object(value, source, "text component");
        fields(component, source, "text", "extra");
        StringBuilder result = new StringBuilder(string(component.get("text"), source, "text"));
        if (component.has("extra")) for (JsonElement part : array(component.get("extra"), source, "extra")) result.append(text(part, source));
        return result.toString();
    }
}
