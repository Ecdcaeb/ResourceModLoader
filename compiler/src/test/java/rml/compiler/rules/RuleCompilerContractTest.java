package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rml.compiler.CompileException;
import rml.compiler.lang.TagResolver;
import rml.compiler.source.Project;
import rml.compiler.source.ProjectReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Executable consumer contract; no test framework or game classes required. */
public final class RuleCompilerContractTest {
    private RuleCompilerContractTest() { }

    public static void main(String[] args) throws Exception {
        recipeTagsAndFactories();
        resultComponents();
        predicatesAndModifierConsumers();
        advancementsAndOrder();
        referenceFailures();
        unsupportedAndInvalidSemantics();
        nativeFunctionSchemas();
        System.out.println("RuleCompilerContractTest passed");
    }

    private static void recipeTagsAndFactories() throws Exception {
        try (Pack pack = new Pack()) {
            pack.json("tags/item/base", "{\"values\":[\"stone\",\"dirt\"]}");
            pack.json("tags/item/all", "{\"values\":[\"#demo:base\",\"stone\",\"sand\"]}");
            pack.json("recipe/shaped", "{\"type\":\"crafting_shaped\",\"group\":\"tools\",\"pattern\":[\"AA\",\" B\"],\"key\":{\"A\":\"#demo:all\",\"B\":\"stick\"},\"result\":{\"id\":\"wooden_pickaxe\"}}");
            pack.json("recipe/shapeless", "{\"type\":\"crafting_shapeless\",\"ingredients\":[{\"tag\":\"demo:base\"},[\"stick\",{\"item\":\"coal\",\"data\":1}]],\"result\":{\"id\":\"torch\",\"count\":4}}");
            pack.json("recipe/smelt", "{\"type\":\"smelting\",\"ingredient\":\"#demo:base\",\"result\":\"glass\",\"experience\":0.5,\"cookingtime\":200}");
            pack.json("recipe/brew", "{\"type\":\"brewing\",\"input\":\"potion\",\"ingredient\":{\"tag\":\"demo:base\"},\"output\":{\"id\":\"potion\",\"components\":{\"custom_data\":{\"Potion\":\"minecraft:healing\"}}}}");
            pack.json("recipe/anvil", "{\"type\":\"rml:anvil\",\"left\":\"#demo:base\",\"right\":\"stick\",\"output\":{\"item\":\"stone\"},\"level\":2}");
            Map<String, JsonElement> compiled = pack.compile();
            JsonObject shaped = rule(compiled, "recipe", "shaped");
            equal("minecraft:crafting_shaped", shaped.get("type").getAsString());
            equal("tools", shaped.get("group").getAsString());
            json("[{\"item\":\"minecraft:stone\"},{\"item\":\"minecraft:dirt\"},{\"item\":\"minecraft:sand\"}]", shaped.getAsJsonObject("key").get("A"));
            json("{\"item\":\"minecraft:wooden_pickaxe\"}", shaped.get("result"));
            json("{\"item\":\"minecraft:torch\",\"count\":4}", rule(compiled, "recipe", "shapeless").get("result"));
            JsonObject smelt = rule(compiled, "recipe", "smelt");
            equal("rml:smelt", smelt.get("type").getAsString());
            json("{\"item\":\"minecraft:stone\"}", smelt.get("input"));
            json("{\"item\":\"minecraft:dirt\"}", compiled.get("recipe|demo:smelt/__rml_smelting/1").getAsJsonObject().get("input"));
            equal(0.5, smelt.get("xp").getAsDouble());
            equal("rml:brew", rule(compiled, "recipe", "brew").get("type").getAsString());
            check(rule(compiled, "recipe", "brew").get("ingredient").isJsonArray(), "Brewing ingredient must retain alternatives");
            check(rule(compiled, "recipe", "anvil").get("left").isJsonArray(), "Anvil ingredient must retain alternatives");
            equal(6, compiled.size());
        }
    }

    private static void resultComponents() throws Exception {
        try (Pack pack = new Pack()) {
            pack.json("recipe/stack", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"diamond_sword\",\"count\":1,\"components\":{\"custom_data\":{\"answer\":42,\"nested\":{\"enabled\":true}},\"custom_name\":{\"text\":\"Blade\",\"extra\":[{\"text\":\"!\"}]},\"lore\":[\"One\",{\"text\":\"Two\"}],\"damage\":3,\"unbreakable\":true,\"repair_cost\":2,\"can_break\":[\"stone\"],\"can_place_on\":[\"dirt\"],\"enchantments\":{\"levels\":{\"sharpness\":3},\"show_in_tooltip\":false}}}}");
            JsonObject result = rule(pack.compile(), "recipe", "stack").getAsJsonObject("result");
            equal("rml:enchantmented_item", result.get("type").getAsString());
            json("[{\"name\":\"minecraft:sharpness\",\"level\":3}]", result.get("enchantment"));
            JsonObject item = result.getAsJsonObject("item");
            equal("minecraft:diamond_sword", item.get("item").getAsString());
            equal(3, item.get("data").getAsInt());
            String nbt = item.get("nbt").getAsString();
            for (String fragment : Arrays.asList("\"answer\":42", "\"enabled\":1b", "\"Name\":\"Blade!\"", "\"Lore\":[\"One\",\"Two\"]", "\"Unbreakable\":1b", "\"RepairCost\":2", "\"CanDestroy\":[\"minecraft:stone\"]", "\"CanPlaceOn\":[\"minecraft:dirt\"]", "\"HideFlags\":1")) check(nbt.contains(fragment), "Missing NBT fragment: " + fragment);
        }
        try (Pack pack = new Pack()) {
            pack.json("recipe/adventure", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"components\":{\"can_break\":{\"predicates\":[{\"blocks\":[\"stone\",\"dirt\"]}],\"show_in_tooltip\":false}}}}");
            equal("{\"HideFlags\":8,\"CanDestroy\":[\"minecraft:stone\",\"minecraft:dirt\"]}", rule(pack.compile(), "recipe", "adventure").getAsJsonObject("result").get("nbt").getAsString());
        }
    }

    private static void predicatesAndModifierConsumers() throws Exception {
        try (Pack pack = new Pack()) {
            pack.json("predicate/base", "{\"condition\":\"random_chance\",\"chance\":0.5}");
            pack.json("predicate/nested", "[{\"condition\":\"reference\",\"name\":\"demo:base\"},{\"condition\":\"any_of\",\"terms\":[{\"condition\":\"inverted\",\"term\":{\"condition\":\"killed_by_player\"}},{\"condition\":\"entity_properties\",\"entity\":\"this\",\"properties\":{\"on_fire\":true}}]}]");
            pack.json("item_modifier/base", "[{\"function\":\"set_count\",\"count\":2,\"conditions\":[{\"condition\":\"reference\",\"name\":\"demo:nested\"}]},{\"function\":\"set_name\",\"name\":{\"text\":\"Reward\"}}]");
            pack.json("item_modifier/all", "[{\"function\":\"reference\",\"name\":\"demo:base\"},{\"function\":\"set_lore\",\"replace\":true,\"lore\":[\"Line\"]},{\"function\":\"set_damage\",\"damage\":0.75}]");
            pack.json("loot_table/reward", "{\"type\":\"chest\",\"pools\":[{\"name\":\"main\",\"rolls\":1,\"conditions\":[{\"condition\":\"reference\",\"name\":\"demo:nested\"}],\"entries\":[{\"type\":\"item\",\"name\":\"diamond_sword\",\"functions\":[{\"function\":\"reference\",\"name\":\"demo:all\"},{\"function\":\"set_count\",\"count\":3}]}]}]}");
            Map<String, JsonElement> output = pack.compile();
            JsonObject predicate = rule(output, "predicate", "nested");
            equal("rml:all_of", predicate.get("condition").getAsString());
            JsonObject any = predicate.getAsJsonArray("terms").get(1).getAsJsonObject();
            equal("rml:any_of", any.get("condition").getAsString());
            JsonObject inverted = any.getAsJsonArray("terms").get(0).getAsJsonObject();
            equal("rml:inverted", inverted.get("condition").getAsString());
            equal("minecraft:killed_by_player", inverted.getAsJsonObject("term").get("condition").getAsString());
            JsonObject pool = rule(output, "loot_table", "reward").getAsJsonArray("pools").get(0).getAsJsonObject();
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            equal("item", entry.get("type").getAsString());
            equal("minecraft:diamond_sword", entry.get("name").getAsString());
        }
    }

    private static void nativeFunctionSchemas() throws Exception {
        try (Pack pack = new Pack()) {
            pack.json("predicate/native", "[{\"condition\":\"entity_scores\",\"entity\":\"killer_player\",\"scores\":{\"points\":{\"min\":2}}},{\"condition\":\"random_chance_with_looting\",\"chance\":0.1,\"looting_multiplier\":0.2}]");
            pack.json("item_modifier/native", "[{\"function\":\"enchant_randomly\",\"enchantments\":[\"sharpness\"]},{\"function\":\"enchant_with_levels\",\"levels\":{\"min\":1,\"max\":3},\"treasure\":true},{\"function\":\"exploration_map\",\"destination\":\"Mansion\",\"decoration\":\"mansion\",\"zoom\":2,\"search_radius\":50,\"skip_existing_chunks\":true},{\"function\":\"furnace_smelt\"},{\"function\":\"looting_enchant\",\"count\":{\"min\":0,\"max\":1},\"limit\":3},{\"function\":\"set_attributes\",\"modifiers\":[{\"name\":\"power\",\"attribute\":\"generic.attackDamage\",\"operation\":\"addition\",\"amount\":2,\"slot\":[\"mainhand\",\"offhand\"],\"id\":\"11111111-2222-3333-4444-555555555555\"}]},{\"function\":\"set_count\",\"count\":{\"min\":1,\"max\":2}},{\"function\":\"set_damage\",\"damage\":{\"min\":0.25,\"max\":0.75}},{\"function\":\"set_data\",\"data\":1},{\"function\":\"set_nbt\",\"tag\":\"{answer:42}\",\"conditions\":[{\"condition\":\"reference\",\"name\":\"demo:native\"}]}]");
            JsonArray functions = pack.compile().get("item_modifier|demo:native").getAsJsonArray();
            equal(10, functions.size());
            equal("minecraft:sharpness", functions.get(0).getAsJsonObject().getAsJsonArray("enchantments").get(0).getAsString());
        }
    }

    private static void advancementsAndOrder() throws Exception {
        try (Pack pack = new Pack()) {
            pack.function("give", "say reward\n");
            pack.json("tags/function/reward", "{\"values\":[\"demo:give\"]}");
            pack.json("advancement/root", "{\"criteria\":{\"tick\":{\"trigger\":\"tick\"}}}");
            pack.json("advancement/child", "{\"parent\":\"demo:root\",\"criteria\":{\"tick\":{\"trigger\":\"tick\"}},\"requirements\":[[\"tick\"]],\"display\":{\"title\":{\"text\":\"Title\"},\"description\":\"Description\",\"icon\":{\"id\":\"stone\"}},\"rewards\":{\"experience\":3,\"function\":\"#demo:reward\"}}");
            Project project = pack.read();
            Map<String, List<String>> tags = TagResolver.resolve(project, "function", Collections.singleton("demo:give"));
            Map<String, JsonElement> output = RuleCompiler.compile(project, tags);
            equal("demo:__rml/tags/demo/reward", rule(output, "advancement", "child").getAsJsonObject("rewards").get("function").getAsString());
            json("{\"item\":\"minecraft:stone\"}", rule(output, "advancement", "child").getAsJsonObject("display").get("icon"));
            List<String> keys = new ArrayList<>(output.keySet());
            List<String> sorted = new ArrayList<>(keys);
            Collections.sort(sorted);
            equal(sorted, keys);
            equal(output, RuleCompiler.compile(project, tags));
            equal(2, output.size());
        }
    }

    private static void referenceFailures() throws Exception {
        try (Pack pack = new Pack()) {
            pack.json("predicate/a", "{\"condition\":\"reference\",\"name\":\"demo:b\"}");
            expect(pack, "missing_predicate", "predicate/a");
            pack.json("predicate/b", "{\"condition\":\"inverted\",\"term\":{\"condition\":\"reference\",\"name\":\"demo:a\"}}");
            expect(pack, "predicate_cycle", "predicate/b");
        }
        try (Pack pack = new Pack()) {
            pack.json("item_modifier/a", "{\"function\":\"reference\",\"name\":\"demo:b\"}");
            expect(pack, "missing_modifier", "item_modifier/a");
            pack.json("item_modifier/b", "{\"function\":\"reference\",\"name\":\"demo:a\"}");
            expect(pack, "modifier_cycle", "item_modifier/b");
        }
        try (Pack pack = new Pack()) {
            pack.json("advancement/a", "{\"parent\":\"demo:b\",\"criteria\":{\"x\":{\"trigger\":\"tick\"}}}");
            expect(pack, "missing_advancement", "advancement/a");
            pack.json("advancement/b", "{\"parent\":\"demo:a\",\"criteria\":{\"x\":{\"trigger\":\"tick\"}}}");
            expect(pack, "advancement_cycle", "advancement/a");
        }
        try (Pack pack = new Pack()) {
            pack.json("recipe/smelt", "{\"type\":\"smelting\",\"ingredient\":[\"stone\",\"dirt\"],\"result\":\"glass\"}");
            pack.json("recipe/smelt/__rml_smelting/1", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":\"stone\"}");
            expect(pack, "generated_id_collision", "recipe/smelt");
        }
    }

    private static void unsupportedAndInvalidSemantics() throws Exception {
        failure("recipe/a", "{\"type\":\"smelting\",\"ingredient\":\"stone\",\"result\":\"glass\",\"cookingtime\":100}", "unsupported_recipe");
        failure("recipe/a", "{\"type\":\"crafting_shaped\",\"pattern\":[\"AB\"],\"key\":{\"A\":\"stone\"},\"result\":\"stone\"}", "invalid_recipe");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"#demo:missing\"],\"result\":\"stone\"}", "missing_tag");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"components\":{\"food\":{}}}}", "unsupported_component");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"components\":{\"custom_name\":{\"translate\":\"a\"}}}}", "unsupported_field");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"data\":1,\"components\":{\"damage\":2}}}", "invalid_rule");
        failure("predicate/a", "{\"condition\":\"weather_check\",\"raining\":true}", "unsupported_condition");
        failure("predicate/a", "{\"condition\":\"entity_properties\",\"entity\":\"this\",\"predicate\":{}}", "unsupported_field");
        failure("predicate/a", "{\"condition\":\"random_chance\",\"chance\":2}", "invalid_rule");
        failure("item_modifier/a", "{\"function\":\"set_count\",\"count\":1,\"add\":true}", "unsupported_field");
        failure("item_modifier/a", "{\"function\":\"set_lore\",\"lore\":[\"append\"]}", "unsupported_function");
        failure("loot_table/a", "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"tag\",\"name\":\"demo:a\"}]}]}", "unsupported_loot_entry");
        failure("advancement/a", "{\"criteria\":{\"x\":{\"trigger\":\"minecraft:using_item\"}}}", "unsupported_trigger");
        failure("advancement/a", "{\"criteria\":{\"x\":{\"trigger\":\"tick\",\"conditions\":{\"player\":[]}}}}", "unsupported_field");
        failure("advancement/a", "{\"criteria\":{\"x\":{\"trigger\":\"consume_item\",\"conditions\":{\"item\":{\"components\":{}}}}}}", "unsupported_field");
        failure("advancement/a", "{\"criteria\":{\"x\":{\"trigger\":\"tick\"}},\"rewards\":{\"function\":\"#demo:missing\"}}", "missing_function_tag");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"components\":{\"custom_data\":{\"mixed\":[1,true]}}}}", "unsupported_component");
        failure("recipe/a", "{\"type\":\"crafting_shapeless\",\"ingredients\":[\"stone\"],\"result\":{\"id\":\"stone\",\"components\":{\"can_break\":{\"predicates\":[{\"blocks\":\"stone\",\"state\":{}}]}}}}", "unsupported_field");
    }

    private static JsonObject rule(Map<String, JsonElement> output, String type, String id) {
        JsonElement result = output.get(type + "|demo:" + id);
        check(result != null, "Missing compiled rule " + type + "|demo:" + id);
        return result.getAsJsonObject();
    }

    private static void failure(String path, String json, String code) throws Exception {
        try (Pack pack = new Pack()) { pack.json(path, json); expect(pack, code, path); }
    }

    private static void expect(Pack pack, String code, String path) throws Exception {
        try { pack.compile(); }
        catch (CompileException failure) {
            equal(code, failure.getCode());
            equal("data/demo/" + path + ".json", failure.getSource());
            equal(0, failure.getLine());
            return;
        }
        throw new AssertionError("Expected " + code + " at " + path);
    }

    private static void json(String expected, JsonElement actual) { equal(new JsonParser().parse(expected), actual); }
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Pack implements AutoCloseable {
        private final Path root;
        Pack() throws IOException {
            root = Files.createTempDirectory("rml-rules-contract-");
            write("pack.mcmeta", "{\"pack\":{\"description\":\"Rules contract\",\"pack_format\":121},\"rml\":{\"id\":\"demo\"}}");
        }
        void json(String path, String content) throws IOException { write("data/demo/" + path + ".json", content); }
        void function(String path, String content) throws IOException { write("data/demo/function/" + path + ".mcfunction", content); }
        private void write(String path, String content) throws IOException {
            Path target = root.resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
        }
        Project read() throws IOException { return ProjectReader.read(root); }
        Map<String, JsonElement> compile() throws IOException { return RuleCompiler.compile(read(), Collections.<String, List<String>>emptyMap()); }
        @Override public void close() throws IOException {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
