package rml.compiler.lang;

import rml.compiler.CompileException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable consumer contract; run with assertions enabled or disabled. */
public final class LanguageContractTest {
    private LanguageContractTest() { }

    public static void main(String[] args) throws Exception {
        tagOrderAndOptionalReferences();
        tagFailures();
        functionSourcesAndExtensions();
        functionRuntimeArgumentsAndExecuteContexts();
        functionFailures();
        deterministicAndImmutableResults();
        System.out.println("LanguageContractTest passed");
    }

    private static void tagOrderAndOptionalReferences() throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("tags/item/nested", "{\"replace\":true,\"values\":[\"stone\",\"demo:gem\",\"stone\"]}");
            pack.put("tags/item/all", "{\"values\":[\"demo:first\",\"#demo:nested\",\"demo:gem\",{\"id\":\"missing\",\"required\":false},{\"id\":\"#demo:absent\",\"required\":false},\"dirt\"]}");
            Project project = pack.read();
            Map<String, List<String>> tags = TagResolver.resolve(project, "item", set("demo:first", "minecraft:stone", "demo:gem", "minecraft:dirt"));
            equal(Arrays.asList("demo:first", "minecraft:stone", "demo:gem", "minecraft:dirt"), tags.get("demo:all"));
            equal(Arrays.asList("demo:all", "demo:nested"), new ArrayList<>(tags.keySet()));
            // A null registry snapshot still validates identifiers but keeps unknown direct entries.
            equal(Arrays.asList("demo:first", "minecraft:stone", "demo:gem", "minecraft:missing", "minecraft:dirt"),
                    TagResolver.resolve(project, "item", null).get("demo:all"));
        }
        try (Pack pack = new Pack()) {
            pack.put("tags/worldgen/biome/cold", "{\"values\":[\"snowy_plains\"]}");
            equal(Collections.singletonList("minecraft:snowy_plains"), TagResolver.resolve(pack.read(), "worldgen/biome", null).get("demo:cold"));
        }
    }

    private static void tagFailures() throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("tags/item/a", "{\"values\":[\"#demo:b\"]}");
            expect("missing_tag", "data/demo/tags/item/a.json", 0, () -> TagResolver.resolve(pack.read(), "item", null));
            pack.put("tags/item/b", "{\"values\":[{\"id\":\"#demo:a\",\"required\":false}]}");
            expect("tag_cycle", "data/demo/tags/item/b.json", 0, () -> TagResolver.resolve(pack.read(), "item", null));
        }
        try (Pack pack = new Pack()) {
            pack.put("tags/item/a", "{\"values\":[\"missing\"]}");
            expect("missing_tag_entry", "data/demo/tags/item/a.json", 0, () -> TagResolver.resolve(pack.read(), "item", Collections.emptySet()));
        }
        String[] malformed = {"[]", "{}", "{\"values\":null}", "{\"values\":[1]}",
                "{\"values\":[{\"required\":false}]}", "{\"values\":[{\"id\":\"stone\",\"required\":\"false\"}]}",
                "{\"values\":[],\"replace\":1}", "{\"values\":[],\"unknown\":true}",
                "{\"values\":[{\"id\":\"stone\",\"unknown\":true}]}"};
        for (String json : malformed) {
            try (Pack pack = new Pack()) {
                pack.put("tags/item/a", json);
                expect("invalid_tag", "data/demo/tags/item/a.json", 0, () -> TagResolver.resolve(pack.read(), "item", null));
            }
        }
        try (Pack pack = new Pack()) {
            pack.put("tags/item/a", "{\"values\":[{\"id\":\"#Bad\",\"required\":false}]}");
            expect("invalid_identifier", "data/demo/tags/item/a.json", 0, () -> TagResolver.resolve(pack.read(), "item", null));
        }
    }

    private static void functionSourcesAndExtensions() throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("function/leaf", "# comment\r\n$say $(greeting) $(reward) $(flag)\r\nsay $(literal)\r\n");
            pack.put("function/main", "\n#include demo:leaf\n#include demo:leaf\n"
                    + "say first \\\n  second\n"
                    + "function demo:main\nfunction outside\nfunction #demo:all\n"
                    + "function demo:leaf if @a\n"
                    + "execute if predicate demo:ready run function #demo:all\n"
                    + "execute unless predicate demo:ready run say no\n"
                    + "execute @a ~ ~ ~ function #demo:all\n"
                    + "execute @a ~ ~ ~ detect ~ ~-1 ~ minecraft:stone 0 function outside\n"
                    + "schedule function #demo:all 2s append\n"
                    + "schedule function outside 20t replace\n"
                    + "schedule function demo:leaf 1.5s\n"
                    + "schedule clear #demo:all\n"
                    + "item modify entity @s weapon.mainhand demo:gift\n"
                    + "say native command  spacing\n");
            pack.put("predicate/ready", "{\"condition\":\"minecraft:killed_by_player\"}");
            pack.put("item_modifier/gift", "{\"function\":\"minecraft:set_count\",\"count\":1}");
            pack.put("tags/function/all", "{\"values\":[\"demo:leaf\",\"outside\",\"demo:leaf\"]}");
            Project project = pack.read();
            Map<String, List<String>> tags = TagResolver.resolve(project, "function", set("demo:leaf", "demo:main", "minecraft:outside"));
            Map<String, List<String>> result = FunctionCompiler.compile(project, tags);
            String wrapper = "demo:__rml/tags/demo/all";
            equal(Arrays.asList("function demo:leaf", "function minecraft:outside"), result.get(wrapper));
            equal(Arrays.asList("say hello 3 true", "say $(literal)"), result.get("demo:leaf"));
            equal(Arrays.asList("say hello 3 true", "say $(literal)", "say hello 3 true", "say $(literal)",
                    "say first second", "function demo:main", "function minecraft:outside", "function " + wrapper,
                    "function demo:leaf if @a", "rml execute if predicate demo:ready run function " + wrapper,
                    "rml execute unless predicate demo:ready run say no", "execute @a ~ ~ ~ function " + wrapper,
                    "execute @a ~ ~ ~ detect ~ ~-1 ~ minecraft:stone 0 function minecraft:outside",
                    "rml schedule function " + wrapper + " 2s append", "rml schedule function minecraft:outside 20t replace",
                    "rml schedule function demo:leaf 1.5s", "rml schedule clear " + wrapper,
                    "rml item modify entity @s weapon.mainhand demo:gift", "say native command  spacing"), result.get("demo:main"));
        }
        // Calls may recurse; includes may not. Default namespaces also apply to source includes and local resources.
        try (Pack pack = new Pack()) {
            pack.putPath("data/minecraft/function/helper.mcfunction", "function demo:main\n");
            pack.putPath("data/minecraft/predicate/ready.json", "{}");
            pack.putPath("data/minecraft/item_modifier/gift.json", "{}");
            pack.put("function/main", "#include helper\nexecute if predicate ready run item modify entity @s 0 gift\n");
            equal(Arrays.asList("function demo:main", "rml execute if predicate minecraft:ready run rml item modify entity @s 0 minecraft:gift"),
                    FunctionCompiler.compile(pack.read(), Collections.emptyMap()).get("demo:main"));
        }
    }

    private static void functionRuntimeArgumentsAndExecuteContexts() throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("function/main", "function demo:echo with storage demo:runtime arguments\n"
                    + "execute as @a at @s if entity @s run say context\n");
            pack.put("function/echo", "$say $(name)\n");
            Map<String, List<String>> compiled = FunctionCompiler.compile(pack.read(), Collections.emptyMap());
            equal(Arrays.asList("function demo:echo with storage demo:runtime arguments",
                    "rml execute as @a at @s if entity @s run say context"), compiled.get("demo:main"));
            equal(Collections.singletonList("$say $(name)"), compiled.get("demo:echo"));
        }
        try (Pack pack = new Pack()) {
            pack.put("function/dialog", "$data modify storage demo:dialog value set value $(value)\n");
            equal(Collections.singletonList("$data modify storage demo:dialog value set value $(value)"),
                    FunctionCompiler.compile(pack.read(), Collections.emptyMap()).get("demo:dialog"));
        }
    }

    private static void functionFailures() throws Exception {
        functionError("\n/say bad\n", "leading_slash", 2);
        functionError("say broken \\\n", "dangling_continuation", 1);
        functionError("say broken \\" , "dangling_continuation", 1);
        functionError("$say $()\n", "invalid_macro", 1);
        functionError("$say $(greeting\n", "invalid_macro", 1);
        functionError("#include demo:missing\n", "missing_include", 1);
        functionError("#include outside\n", "missing_include", 1);
        functionError("#include\n", "invalid_include", 1);
        functionError("#include demo:main extra\n", "invalid_include", 1);
        functionError("function demo:missing\n", "missing_function", 1);
        functionError("function #demo:missing\n", "missing_function_tag", 1);
        functionError("function Bad\n", "invalid_identifier", 1);
        functionError("execute if predicate demo:missing run say x\n", "missing_predicate", 1);
        functionError("execute unless predicate demo:missing\n", "unsupported_command", 1);
        functionError("item modify entity @s 0 demo:missing\n", "missing_item_modifier", 1);
        functionError("item modify block ~ ~ ~ 0 demo:missing\n", "missing_item_modifier", 1);
        functionError("schedule function demo:main\n", "invalid_schedule", 1);
        functionError("schedule function demo:main nope\n", "invalid_schedule", 1);
        functionError("schedule function demo:main 1t invalid\n", "invalid_schedule", 1);
        functionError("schedule clear demo:main extra\n", "invalid_schedule", 1);
        functionError("schedule function demo:missing 1t\n", "missing_function", 1);
        try (Pack pack = new Pack()) {
            pack.put("function/main", "#include demo:other\n");
            pack.put("function/other", "# comment\n#include demo:main\n");
            expect("include_cycle", "data/demo/function/other.mcfunction", 2,
                    () -> FunctionCompiler.compile(pack.read(), Collections.emptyMap()));
        }
        try (Pack pack = new Pack()) {
            pack.put("function/main", "#include demo:other\n");
            pack.put("function/other", "# comment\nfunction demo:missing\n");
            expect("missing_function", "data/demo/function/other.mcfunction", 2,
                    () -> FunctionCompiler.compile(pack.read(), Collections.emptyMap()));
        }
        try (Pack pack = new Pack()) {
            pack.put("function/__rml/tags/demo/all", "say collision\n");
            pack.put("tags/function/all", "{\"values\":[]}");
            Project project = pack.read();
            expect("wrapper_collision", "data/demo/tags/function/all.json", 0,
                    () -> FunctionCompiler.compile(project, TagResolver.resolve(project, "function", Collections.emptySet())));
        }
        try (Pack pack = new Pack()) {
            pack.putPath("pack.mcmeta", "{\"pack\":{\"description\":\"test\",\"pack_format\":121},"
                    + "\"rml\":{\"id\":\"demo\",\"constants\":{\"bad\":\"one\\ntwo\"}}}");
            pack.put("function/main", "$say $(bad)\n");
            expect("invalid_metadata", "pack.mcmeta", 0,
                    () -> FunctionCompiler.compile(pack.read(), Collections.emptyMap()));
        }
    }

    private static void deterministicAndImmutableResults() throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("function/z", "say z\n");
            pack.put("function/a", "say a\n");
            pack.put("tags/function/empty", "{\"values\":[]}");
            Project project = pack.read();
            Map<String, List<String>> tags = TagResolver.resolve(project, "function", Collections.emptySet());
            Map<String, List<String>> first = FunctionCompiler.compile(project, tags);
            equal(first, FunctionCompiler.compile(project, tags));
            equal(Arrays.asList("demo:__rml/tags/demo/empty", "demo:a", "demo:z"), new ArrayList<>(first.keySet()));
            equal(Collections.emptyList(), first.get("demo:__rml/tags/demo/empty"));
            expectImmutable(() -> first.put("demo:new", Collections.emptyList()));
            expectImmutable(() -> first.get("demo:a").add("say mutation"));
            expectImmutable(() -> tags.get("demo:empty").add("demo:a"));
        }
    }

    private static void functionError(String text, String code, int line) throws Exception {
        try (Pack pack = new Pack()) {
            pack.put("function/main", text);
            expect(code, "data/demo/function/main.mcfunction", line,
                    () -> FunctionCompiler.compile(pack.read(), Collections.emptyMap()));
        }
    }

    private static Set<String> set(String... entries) { return new LinkedHashSet<>(Arrays.asList(entries)); }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void expect(String code, String source, int line, Action action) throws Exception {
        try {
            action.run();
        } catch (CompileException e) {
            equal(code, e.getCode());
            equal(source, e.getSource().replace('\\', '/'));
            equal(line, e.getLine());
            return;
        }
        throw new AssertionError("Expected " + code + " at " + source + ":" + line);
    }

    private static void expectImmutable(Action action) throws Exception {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected immutable result");
    }

    private interface Action { void run() throws Exception; }

    private static final class Pack implements AutoCloseable {
        private final Path root;

        private Pack() throws IOException {
            root = Files.createTempDirectory("rml-language-contract-");
            putPath("pack.mcmeta", "{\"pack\":{\"pack_format\":121,\"description\":\"Language contract\"},"
                    + "\"rml\":{\"id\":\"demo\",\"constants\":{\"greeting\":\"hello\",\"reward\":3,\"flag\":true},"
                    + "\"external_functions\":[\"outside\"]}}");
        }

        private void put(String resource, String text) throws IOException {
            putPath("data/demo/" + resource + (resource.startsWith("function/") ? ".mcfunction" : ".json"), text);
        }

        private void putPath(String relative, String text) throws IOException {
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, text.getBytes(StandardCharsets.UTF_8));
        }

        private Project read() throws IOException { return ProjectReader.read(root); }

        @Override
        public void close() throws IOException {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
