package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import rml.compiler.SourceResource;
import rml.compiler.lang.TagResolver;
import rml.compiler.source.Project;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static rml.compiler.rules.RuleJson.*;

/** Lowers target-native recipes and enhanced machine recipes with source identities intact. */
final class RecipeLowering {
    private final Project project;
    private Map<String, List<String>> tags;

    RecipeLowering(Project project) { this.project = project; }

    void compile(SourceResource source, Map<String, JsonElement> output) {
        JsonObject input = object(source.getJson(), source, "recipe");
        String type = type(input, "type", source);
        JsonObject result = new JsonObject();
        switch (type) {
            case "minecraft:crafting_shaped":
                fields(input, source, "type", "group", "pattern", "key", "result");
                result.addProperty("type", type);
                group(input, result, source);
                JsonArray pattern = array(input.get("pattern"), source, "pattern");
                if (pattern.size() < 1 || pattern.size() > 3) throw error(source, "invalid_recipe", "Shaped pattern must have 1..3 rows");
                Set<Character> used = new LinkedHashSet<>();
                int width = -1;
                for (JsonElement rowValue : pattern) {
                    String row = string(rowValue, source, "pattern row");
                    if (row.length() < 1 || row.length() > 3 || width != -1 && row.length() != width) throw error(source, "invalid_recipe", "Pattern rows must have equal width 1..3");
                    width = row.length();
                    for (char c : row.toCharArray()) if (c != ' ') used.add(c);
                }
                if (used.isEmpty()) throw error(source, "invalid_recipe", "Pattern cannot be empty");
                JsonObject keys = object(input.get("key"), source, "key");
                JsonObject compiledKeys = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : keys.entrySet()) {
                    if (entry.getKey().length() != 1 || " ".equals(entry.getKey())) throw error(source, "invalid_recipe", "Recipe keys must be one non-space character");
                    if (!used.remove(entry.getKey().charAt(0))) throw error(source, "invalid_recipe", "Unused recipe key: " + entry.getKey());
                    compiledKeys.add(entry.getKey(), ingredient(entry.getValue(), source));
                }
                if (!used.isEmpty()) throw error(source, "invalid_recipe", "Pattern symbols have no ingredient: " + used);
                result.add("pattern", copy(pattern));
                result.add("key", compiledKeys);
                result.add("result", StackLowering.lower(input.get("result"), source));
                break;
            case "minecraft:crafting_shapeless":
                fields(input, source, "type", "group", "ingredients", "result");
                result.addProperty("type", type);
                group(input, result, source);
                JsonArray ingredients = array(input.get("ingredients"), source, "ingredients");
                if (ingredients.size() < 1 || ingredients.size() > 9) throw error(source, "invalid_recipe", "Shapeless recipes require 1..9 ingredients");
                JsonArray lowered = new JsonArray();
                for (JsonElement entry : ingredients) lowered.add(ingredient(entry, source));
                result.add("ingredients", lowered);
                result.add("result", StackLowering.lower(input.get("result"), source));
                break;
            case "minecraft:smelting":
            case "rml:smelt":
                boolean modern = "minecraft:smelting".equals(type);
                if (modern) fields(input, source, "type", "ingredient", "result", "experience", "cookingtime");
                else fields(input, source, "type", "input", "output", "xp");
                if (input.has("cookingtime") && integer(input.get("cookingtime"), source, "cookingtime", 1, Integer.MAX_VALUE) != 200) {
                    throw error(source, "unsupported_recipe", "The 1.12 furnace supports only cookingtime 200");
                }
                JsonElement alternatives = ingredient(input.get(modern ? "ingredient" : "input"), source);
                List<JsonElement> inputs = new ArrayList<>();
                if (alternatives.isJsonArray()) for (JsonElement entry : alternatives.getAsJsonArray()) inputs.add(entry);
                else inputs.add(alternatives);
                result.addProperty("type", "rml:smelt");
                result.add("output", StackLowering.lower(input.get(modern ? "result" : "output"), source));
                String xpField = modern ? "experience" : "xp";
                double xp = input.has(xpField) ? number(input.get(xpField), source, xpField) : 0;
                if (xp < 0 || xp > Float.MAX_VALUE) throw error(source, "invalid_recipe", "Experience must be a nonnegative float");
                result.addProperty("xp", xp);
                for (int i = 0; i < inputs.size(); i++) {
                    JsonObject expanded = (JsonObject) copy(result);
                    expanded.add("input", inputs.get(i));
                    add(output, source, i == 0 ? source.getId() : source.getId() + "/__rml_smelting/" + i, expanded);
                }
                return;
            case "minecraft:brewing":
            case "rml:brew":
                fields(input, source, "type", "input", "ingredient", "output");
                result.addProperty("type", "rml:brew");
                result.add("input", ingredient(input.get("input"), source));
                result.add("ingredient", ingredient(input.get("ingredient"), source));
                result.add("output", StackLowering.lower(input.get("output"), source));
                break;
            case "rml:anvil":
                fields(input, source, "type", "left", "right", "output", "level");
                result.addProperty("type", type);
                result.add("left", ingredient(input.get("left"), source));
                result.add("right", ingredient(input.get("right"), source));
                result.add("output", StackLowering.lower(input.get("output"), source));
                result.addProperty("level", integer(input.get("level"), source, "level", 0, Integer.MAX_VALUE));
                break;
            case "minecraft:blasting":
            case "minecraft:smoking":
            case "minecraft:campfire_cooking":
                machineCooking(input, result, source, type);
                output.put("machine_recipe|" + source.getId(), result);
                return;
            case "minecraft:stonecutting":
                fields(input, source, "type", "group", "category", "ingredient", "result");
                result.addProperty("type", type);
                group(input, result, source);
                if (input.has("category")) result.add("category", copy(input.get("category")));
                result.add("ingredient", ingredient(input.get("ingredient"), source));
                result.add("result", machineResult(input.get("result"), source));
                output.put("machine_recipe|" + source.getId(), result);
                return;
            case "minecraft:smithing_transform":
                fields(input, source, "type", "template", "base", "addition", "result");
                result.addProperty("type", type);
                result.add("template", ingredient(input.get("template"), source));
                result.add("base", ingredient(input.get("base"), source));
                result.add("addition", ingredient(input.get("addition"), source));
                result.add("result", machineResult(input.get("result"), source));
                output.put("machine_recipe|" + source.getId(), result);
                return;
            case "minecraft:smithing_trim":
                fields(input, source, "type", "template", "base", "addition", "pattern");
                result.addProperty("type", type);
                result.add("template", ingredient(input.get("template"), source));
                result.add("base", ingredient(input.get("base"), source));
                result.add("addition", ingredient(input.get("addition"), source));
                if (input.has("pattern")) result.addProperty("pattern", id(input.get("pattern"), source, "pattern"));
                output.put("machine_recipe|" + source.getId(), result);
                return;
            case "minecraft:crafting_transmute":
                fields(input, source, "type", "group", "category", "input", "material", "result");
                result.addProperty("type", type);
                group(input, result, source);
                if (input.has("category")) result.add("category", copy(input.get("category")));
                result.add("input", ingredient(input.get("input"), source));
                result.add("material", ingredient(input.get("material"), source));
                result.add("result", machineResult(input.get("result"), source));
                output.put("machine_recipe|" + source.getId(), result);
                return;
            default: throw error(source, "unsupported_recipe", "Unsupported recipe factory: " + type);
        }
        add(output, source, source.getId(), result);
    }

    private void add(Map<String, JsonElement> output, SourceResource source, String id, JsonObject result) {
        if (!id.equals(source.getId()) && project.getResource("recipe", id) != null || output.containsKey("recipe|" + id)) {
            throw error(source, "generated_id_collision", "Generated smelting recipe collides with recipe " + id);
        }
        output.put("recipe|" + id, result);
    }

    private void group(JsonObject input, JsonObject output, SourceResource source) {
        if (input.has("group")) output.addProperty("group", string(input.get("group"), source, "group"));
    }

    private void machineCooking(JsonObject input, JsonObject output, SourceResource source, String type) {
        fields(input, source, "type", "category", "group", "ingredient", "result", "experience", "cookingtime");
        output.addProperty("type", type);
        group(input, output, source);
        if (input.has("category")) output.add("category", copy(input.get("category")));
        output.add("ingredient", ingredient(input.get("ingredient"), source));
        output.add("result", machineResult(input.get("result"), source));
        double experience = input.has("experience") ? number(input.get("experience"), source, "experience") : 0.0;
        if (experience < 0 || experience > Float.MAX_VALUE) throw error(source, "invalid_recipe", "Experience must be a nonnegative float");
        output.addProperty("experience", experience);
        int defaultTime = "minecraft:campfire_cooking".equals(type) ? 600 : 200;
        output.addProperty("cookingtime", input.has("cookingtime")
                ? integer(input.get("cookingtime"), source, "cookingtime", 1, Integer.MAX_VALUE) : defaultTime);
    }

    private JsonElement machineResult(JsonElement value, SourceResource source) {
        JsonObject result = object(value, source, "recipe result");
        fields(result, source, "id", "count", "components");
        id(result.get("id"), source, "result id");
        if (result.has("count")) integer(result.get("count"), source, "result count", 1, 99);
        return copy(value);
    }
    private JsonElement ingredient(JsonElement value, SourceResource source) {
        List<JsonObject> alternatives = new ArrayList<>();
        collect(value, source, alternatives);
        if (alternatives.isEmpty()) throw error(source, "empty_ingredient", "Ingredient has no matching items");
        JsonArray result = new JsonArray();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonObject entry : alternatives) if (seen.add(entry.toString())) result.add(entry);
        return result.size() == 1 ? result.get(0) : result;
    }

    private void collect(JsonElement value, SourceResource source, List<JsonObject> output) {
        if (value != null && value.isJsonArray()) {
            if (value.getAsJsonArray().size() == 0) throw error(source, "empty_ingredient", "Ingredient alternatives cannot be empty");
            for (JsonElement entry : value.getAsJsonArray()) collect(entry, source, output);
            return;
        }
        if (value != null && value.isJsonPrimitive()) {
            String text = string(value, source, "ingredient");
            if (text.startsWith("#")) tag(text.substring(1), source, output);
            else {
                JsonObject item = new JsonObject();
                item.addProperty("item", id(value, source, "item"));
                output.add(item);
            }
            return;
        }
        JsonObject ingredient = object(value, source, "ingredient");
        if (ingredient.has("tag")) {
            fields(ingredient, source, "tag");
            tag(string(ingredient.get("tag"), source, "tag"), source, output);
        } else {
            fields(ingredient, source, "item", "data");
            JsonObject item = new JsonObject();
            item.addProperty("item", id(ingredient.get("item"), source, "item"));
            if (ingredient.has("data")) item.addProperty("data", integer(ingredient.get("data"), source, "data", 0, 32767));
            output.add(item);
        }
    }

    private void tag(String text, SourceResource source, List<JsonObject> output) {
        String tag = id(new JsonPrimitive(text), source, "tag");
        if (tags == null) tags = TagResolver.resolve(project, "item", null);
        List<String> members = tags.get(tag);
        if (members == null) throw error(source, "missing_tag", "Unknown item tag: " + tag);
        for (String member : members) {
            JsonObject item = new JsonObject();
            item.addProperty("item", member);
            output.add(item);
        }
    }
}
