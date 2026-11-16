package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.compiler.source.Project;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static rml.compiler.rules.RuleJson.*;

/** Pure Java lowering into rule formats consumed by the 1.12 RML backend. */
public final class RuleCompiler {
    private RuleCompiler() { }

    public static Map<String, JsonElement> compile(Project project, Map<String, List<String>> functionTags) {
        return new Compilation(project, functionTags).compile();
    }

    private static final class Compilation {
        private final Project project;
        private final Map<String, List<String>> functionTags;
        private final Map<String, JsonElement> output = new TreeMap<>();
        private final PredicateLowering predicates;
        private final ModifierLowering modifiers;
        private final RecipeLowering recipes;
        private final Set<String> activeLoot = new LinkedHashSet<>();
        private final Set<String> activeAdvancements = new LinkedHashSet<>();

        Compilation(Project project, Map<String, List<String>> functionTags) {
            this.project = project;
            this.functionTags = functionTags;
            predicates = new PredicateLowering(project);
            modifiers = new ModifierLowering(project, predicates);
            recipes = new RecipeLowering(project);
        }

        Map<String, JsonElement> compile() {
            for (SourceResource resource : project.getResources().values()) {
                switch (resource.getType()) {
                    case "recipe": recipes.compile(resource, output); break;
                    case "predicate": output.put("predicate|" + resource.getId(), predicates.named(resource.getId(), resource)); break;
                    case "item_modifier": output.put("item_modifier|" + resource.getId(), modifiers.named(resource.getId(), resource)); break;
                    case "loot_table": loot(resource); break;
                    case "advancement": advancement(resource); break;
                    default: break; // Other resource families belong to the output/language compilers.
                }
            }
            return Collections.unmodifiableMap(output);
        }

        private void loot(SourceResource source) {
            String key = "loot_table|" + source.getId();
            if (output.containsKey(key)) return;
            if (!activeLoot.add(source.getId())) throw error(source, "loot_table_cycle", "Loot table reference cycle: " + activeLoot);
            try {
                JsonObject input = object(source.getJson(), source, "loot table");
                fields(input, source, "pools", "type");
                if (input.has("type")) {
                    String type = id(input.get("type"), source, "loot table type");
                    if (!Arrays.asList("minecraft:empty", "minecraft:chest", "minecraft:entity", "minecraft:fishing", "minecraft:gameplay").contains(type)) throw error(source, "unsupported_loot_table", "Unsupported 1.12 loot context: " + type);
                }
                JsonObject result = new JsonObject();
                JsonArray pools = new JsonArray();
                if (input.has("pools")) for (JsonElement entry : array(input.get("pools"), source, "pools")) pools.add(pool(entry, source));
                result.add("pools", pools);
                output.put(key, result);
            } finally { activeLoot.remove(source.getId()); }
        }

        private JsonObject pool(JsonElement value, SourceResource source) {
            JsonObject input = object(value, source, "loot pool");
            fields(input, source, "name", "rolls", "bonus_rolls", "entries", "conditions");
            JsonObject result = (JsonObject) copy(input);
            if (input.has("name")) string(input.get("name"), source, "pool name");
            ModifierLowering.range(input.get("rolls"), source, "rolls", 0, Integer.MAX_VALUE, false);
            if (input.has("bonus_rolls")) ModifierLowering.range(input.get("bonus_rolls"), source, "bonus_rolls", 0, Float.MAX_VALUE, false);
            if (input.has("conditions")) result.add("conditions", predicates.conditions(input.get("conditions"), source));
            JsonArray entries = new JsonArray();
            for (JsonElement entry : array(input.get("entries"), source, "entries")) entries.add(lootEntry(entry, source));
            result.add("entries", entries);
            return result;
        }

        private JsonObject lootEntry(JsonElement value, SourceResource source) {
            JsonObject input = object(value, source, "loot entry");
            String type = type(input, "type", source);
            boolean item = "minecraft:item".equals(type);
            boolean table = "minecraft:loot_table".equals(type);
            if (!item && !table && !"minecraft:empty".equals(type)) throw error(source, "unsupported_loot_entry", "Unsupported 1.12 loot entry: " + type);
            if (item) fields(input, source, "type", "name", "weight", "quality", "conditions", "functions", "entryName");
            else if (table) fields(input, source, "type", "name", "weight", "quality", "conditions", "entryName");
            else fields(input, source, "type", "weight", "quality", "conditions", "entryName");
            JsonObject result = (JsonObject) copy(input);
            result.addProperty("type", Ids.path(type));
            if (input.has("weight")) integer(input.get("weight"), source, "weight", 1, Integer.MAX_VALUE);
            if (input.has("quality")) integer(input.get("quality"), source, "quality", Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (input.has("entryName")) string(input.get("entryName"), source, "entryName");
            if (item || table) result.addProperty("name", id(input.get("name"), source, "loot entry name"));
            if (table) {
                String reference = result.get("name").getAsString();
                SourceResource target = project.getResource("loot_table", reference);
                if (target != null) loot(target);
                else if (!"minecraft".equals(Ids.namespace(reference))) throw error(source, "missing_loot_table", "Unknown loot table: " + reference);
            }
            if (input.has("conditions")) result.add("conditions", predicates.conditions(input.get("conditions"), source));
            if (input.has("functions")) {
                array(input.get("functions"), source, "functions");
                result.add("functions", modifiers.functions(input.get("functions"), source));
            }
            return result;
        }

        private void advancement(SourceResource source) {
            String key = "advancement|" + source.getId();
            if (output.containsKey(key)) return;
            if (!activeAdvancements.add(source.getId())) throw error(source, "advancement_cycle", "Advancement parent cycle: " + activeAdvancements);
            try {
                JsonObject input = object(source.getJson(), source, "advancement");
                fields(input, source, "parent", "display", "criteria", "requirements", "rewards");
                JsonObject result = (JsonObject) copy(input);
                if (input.has("parent")) {
                    String parent = id(input.get("parent"), source, "parent");
                    result.addProperty("parent", parent);
                    SourceResource target = project.getResource("advancement", parent);
                    if (target != null) advancement(target);
                    else if (!"minecraft".equals(Ids.namespace(parent))) throw error(source, "missing_advancement", "Unknown parent advancement: " + parent);
                }
                JsonObject criteria = object(input.get("criteria"), source, "criteria");
                if (criteria.entrySet().isEmpty()) throw error(source, "invalid_advancement", "Advancement criteria cannot be empty");
                for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) {
                    JsonObject criterion = object(entry.getValue(), source, "criterion " + entry.getKey());
                    fields(criterion, source, "trigger", "conditions");
                    String trigger = id(criterion.get("trigger"), source, "trigger");
                    validateTrigger(trigger, criterion, source);
                    result.getAsJsonObject("criteria").getAsJsonObject(entry.getKey()).addProperty("trigger", trigger);
                }
                if (input.has("requirements")) requirements(input.get("requirements"), criteria, source);
                if (input.has("display")) display(input.get("display"), source, result);
                if (input.has("rewards")) result.add("rewards", rewards(input.get("rewards"), source));
                output.put(key, result);
            } finally { activeAdvancements.remove(source.getId()); }
        }

        private void display(JsonElement value, SourceResource source, JsonObject advancement) {
            JsonObject input = object(value, source, "display");
            fields(input, source, "title", "description", "icon", "background", "frame", "show_toast", "announce_to_chat", "hidden");
            textComponent(input.get("title"), source);
            textComponent(input.get("description"), source);
            JsonObject icon = object(input.get("icon"), source, "display icon");
            fields(icon, source, "item", "id", "data", "nbt");
            if (icon.has("item") && icon.has("id")) throw error(source, "invalid_advancement", "Icon cannot specify both id and item");
            JsonObject compiledIcon = new JsonObject();
            compiledIcon.addProperty("item", id(icon.get(icon.has("id") ? "id" : "item"), source, "icon item"));
            if (icon.has("data")) compiledIcon.addProperty("data", integer(icon.get("data"), source, "icon data", 0, 32767));
            if (icon.has("nbt")) compiledIcon.addProperty("nbt", string(icon.get("nbt"), source, "icon nbt"));
            advancement.getAsJsonObject("display").add("icon", compiledIcon);
            if (input.has("background")) id(input.get("background"), source, "background");
            if (input.has("frame") && !Arrays.asList("task", "goal", "challenge").contains(string(input.get("frame"), source, "frame"))) throw error(source, "invalid_advancement", "Unknown advancement frame");
            for (String field : Arrays.asList("show_toast", "announce_to_chat", "hidden")) if (input.has(field)) bool(input.get(field), source, field);
        }

        private void textComponent(JsonElement value, SourceResource source) {
            if (value == null || value.isJsonNull() || !(value.isJsonPrimitive() || value.isJsonArray() || value.isJsonObject())) throw error(source, "invalid_advancement", "Missing text component");
        }

        private void requirements(JsonElement value, JsonObject criteria, SourceResource source) {
            Set<String> mentioned = new HashSet<>();
            JsonArray requirements = array(value, source, "requirements");
            if (requirements.size() == 0) throw error(source, "invalid_advancement", "Requirements cannot be empty");
            for (JsonElement group : requirements) {
                JsonArray alternatives = array(group, source, "requirement group");
                if (alternatives.size() == 0) throw error(source, "invalid_advancement", "Requirement groups cannot be empty");
                for (JsonElement criterion : alternatives) {
                    String name = string(criterion, source, "required criterion");
                    if (!criteria.has(name)) throw error(source, "invalid_advancement", "Requirement names unknown criterion: " + name);
                    mentioned.add(name);
                }
            }
            for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) if (!mentioned.contains(entry.getKey())) throw error(source, "invalid_advancement", "Criterion missing from requirements: " + entry.getKey());
        }

        private JsonObject rewards(JsonElement value, SourceResource source) {
            JsonObject input = object(value, source, "rewards");
            fields(input, source, "experience", "loot", "recipes", "function");
            JsonObject result = (JsonObject) copy(input);
            if (input.has("experience")) integer(input.get("experience"), source, "experience", 0, Integer.MAX_VALUE);
            for (String field : Arrays.asList("loot", "recipes")) if (input.has(field)) {
                JsonArray ids = new JsonArray();
                for (JsonElement reference : array(input.get(field), source, field)) {
                    String id = id(reference, source, field);
                    String kind = "loot".equals(field) ? "loot_table" : "recipe";
                    if (project.getResource(kind, id) == null && !"minecraft".equals(Ids.namespace(id))) throw error(source, "missing_reward", "Unknown " + kind + " reward: " + id);
                    ids.add(new JsonPrimitive(id));
                }
                result.add(field, ids);
            }
            if (input.has("function")) {
                String function = string(input.get("function"), source, "reward function");
                if (function.startsWith("#")) {
                    String tag = id(new JsonPrimitive(function.substring(1)), source, "function tag");
                    if (!functionTags.containsKey(tag)) throw error(source, "missing_function_tag", "Unknown reward function tag: " + tag);
                    function = project.getId() + ":__rml/tags/" + Ids.namespace(tag) + "/" + Ids.path(tag);
                } else {
                    function = id(input.get("function"), source, "reward function");
                    if (project.getResource("function", function) == null && !project.getExternalFunctions().contains(function)) throw error(source, "missing_function", "Unknown reward function: " + function);
                }
                result.addProperty("function", function);
            }
            return result;
        }

        private void validateTrigger(String trigger, JsonObject criterion, SourceResource source) {
            if (!"minecraft".equals(Ids.namespace(trigger))) throw error(source, "unsupported_trigger", "Unsupported advancement trigger: " + trigger);
            String fields;
            switch (Ids.path(trigger)) {
                case "impossible": case "tick": case "slept_in_bed": fields = ""; break;
                case "brewed_potion": fields = "potion"; break;
                case "changed_dimension": fields = "from,to"; break;
                case "construct_beacon": fields = "level"; break;
                case "consume_item": case "used_totem": fields = "item"; break;
                case "cured_zombie_villager": fields = "zombie,villager"; break;
                case "effects_changed": fields = "effects"; break;
                case "enchanted_item": fields = "item,levels"; break;
                case "enter_block": fields = "block,state"; break;
                case "entity_hurt_player": fields = "damage"; break;
                case "entity_killed_player": case "player_killed_entity": fields = "entity,killing_blow"; break;
                case "inventory_changed": fields = "slots,items"; break;
                case "item_durability_changed": fields = "item,durability,delta"; break;
                case "levitation": fields = "distance,duration"; break;
                case "location": fields = "position,dimension,biome,feature"; break;
                case "nether_travel": fields = "entered,exited,distance"; break;
                case "placed_block": fields = "block,state,location,item"; break;
                case "player_hurt_entity": fields = "damage,entity"; break;
                case "recipe_unlocked": fields = "recipe"; break;
                case "summoned_entity": case "tame_animal": fields = "entity"; break;
                case "used_ender_eye": fields = "distance"; break;
                case "villager_trade": fields = "villager,item"; break;
                default: throw error(source, "unsupported_trigger", "Unsupported 1.12 advancement trigger: " + trigger);
            }
            if (criterion.has("conditions")) {
                JsonObject conditions = object(criterion.get("conditions"), source, "criterion conditions");
                RuleJson.fields(conditions, source, fields.isEmpty() ? new String[0] : fields.split(","));
                validateNativePredicates(conditions, source);
            }
        }

        /** Catch modern predicate fields that vanilla 1.12 would silently ignore. */
        private void validateNativePredicates(JsonObject conditions, SourceResource source) {
            NativeAdvancement.validate(project, conditions, source);
        }
    }
}
