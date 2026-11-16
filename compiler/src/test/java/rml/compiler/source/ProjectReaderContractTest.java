package rml.compiler.source;

import rml.compiler.CompileException;
import rml.compiler.SourceResource;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Executable fixture contract; no assertion flags or test framework are required. */
public final class ProjectReaderContractTest {
    private static final String MINIMAL = "{\"pack\":{\"pack_format\":121,\"description\":\"Fixture\"},"
            + "\"rml\":{\"id\":\"fixture\"}}";

    private ProjectReaderContractTest() { }

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("rml-project-contract-");
        try {
            parity(temporary);
            defaults(temporary);
            metadataFailures(temporary);
            resourceFailures(temporary);
            archiveFailures(temporary);
        } finally {
            Files.walkFileTree(temporary, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        System.out.println("ProjectReaderContractTest passed");
    }

    private static void parity(Path temporary) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        put(files, "README.md", "Ignored root documentation");
        put(files, "pack.mcmeta", "{\"pack\":{\"description\":{\"text\":\"Rich fixture\",\"color\":\"gold\"},"
                + "\"pack_format\":121},\"rml\":{\"id\":\"fixture\",\"name\":\"A fixture\",\"version\":\"2.3\","
                + "\"minecraft\":\"1.12.2\",\"language_version\":1,\"constants\":{\"z\":true,\"a\":3,\"word\":\"hello\"},"
                + "\"dependencies\":[\"required-after:forge@[14.23.5,)\",\"after:other\"],"
                + "\"external_functions\":[\"native\",\"other:external\",\"minecraft:native\"]}}");
        put(files, "data/fixture/function/nested/run.mcfunction", "say \u4e2d\r\nsay done\n");
        put(files, "data/minecraft/function/a.mcfunction", "say first\n");
        put(files, "data/fixture/tags/function/nested/start.json", "{\"values\":[\"fixture:nested/run\"]}");
        put(files, "data/fixture/tags/worldgen/biome/nested/wet.json", "{\"values\":[\"minecraft:swamp\"]}");
        put(files, "data/fixture/worldgen/configured_feature/nested/tree.json", "{\"type\":\"minecraft:tree\"}");
        put(files, "data/fixture/recipe/crafting/a.json", "{\"type\":\"minecraft:crafting_shapeless\"}");
        put(files, "data/fixture/advancement/nested/a.json", "{}");
        put(files, "data/fixture/loot_table/chests/a.json", "{}");
        put(files, "data/fixture/predicate/a.json", "{}");
        put(files, "data/fixture/item_modifier/a.json", "{}");
        put(files, "data/fixture/dimension/nested/a.json", "{}");
        put(files, "data/fixture/custom_registry/nested/a.json", "{\"never\":\"dropped\"}");
        files.put("data/fixture/structure/nested/home.nbt", new byte[] { 31, -117, 0, 1, -1, 42 });
        files.put("assets/Custom Namespace/Textures/\u4e2d.bin", new byte[] { 0, -1, 13, 10, 42 });
        files.put("assets/fixture/empty.bin", new byte[0]);
        Path directory = temporary.resolve("parity-directory");
        directory(directory, files);
        Path archive = temporary.resolve("parity.zip");
        archive(archive, files);
        Project fromDirectory = ProjectReader.read(directory);
        Project fromZip = ProjectReader.read(archive);
        equal("fixture", fromDirectory.getId(), "id");
        equal("A fixture", fromDirectory.getName(), "name");
        equal("2.3", fromDirectory.getVersion(), "version");
        equal("1.12.2", fromDirectory.getMinecraftVersion(), "target");
        equal("{\"text\":\"Rich fixture\",\"color\":\"gold\"}", fromDirectory.getDescription(), "text component");
        equal(Arrays.asList("a", "word", "z"), new ArrayList<>(fromDirectory.getConstants().keySet()), "sorted constants");
        equal("3", fromDirectory.getConstants().get("a"), "numeric constant");
        equal("true", fromDirectory.getConstants().get("z"), "boolean constant");
        equal(Arrays.asList("required-after:forge@[14.23.5,)", "after:other"), fromDirectory.getDependencies(), "Forge dependency strings");
        equal(Arrays.asList("minecraft:native", "other:external"), new ArrayList<>(fromDirectory.getExternalFunctions()), "canonical external IDs");
        equal(13, fromDirectory.getResources().size(), "every data resource indexed");
        List<String> keys = new ArrayList<>(fromDirectory.getResources().keySet());
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        equal(sorted, keys, "deterministic resource keys");
        equal(keys, new ArrayList<>(fromZip.getResources().keySet()), "directory/ZIP index parity");
        equal(fromDirectory.getId(), fromZip.getId(), "directory/ZIP id parity");
        equal(fromDirectory.getName(), fromZip.getName(), "directory/ZIP name parity");
        equal(fromDirectory.getVersion(), fromZip.getVersion(), "directory/ZIP version parity");
        equal(fromDirectory.getMinecraftVersion(), fromZip.getMinecraftVersion(), "directory/ZIP target parity");
        equal(fromDirectory.getDescription(), fromZip.getDescription(), "directory/ZIP description parity");
        equal(fromDirectory.getConstants(), fromZip.getConstants(), "directory/ZIP constant parity");
        equal(fromDirectory.getDependencies(), fromZip.getDependencies(), "directory/ZIP dependency parity");
        equal(fromDirectory.getExternalFunctions(), fromZip.getExternalFunctions(), "directory/ZIP external parity");
        for (String key : keys) {
            SourceResource resource = fromDirectory.getResources().get(key);
            SourceResource zipped = fromZip.getResources().get(key);
            equal(key, resource.getType() + "|" + resource.getId(), "source identity");
            equal(resource.getPath(), zipped.getPath(), "directory/ZIP source path");
            bytes(files.get(resource.getPath()), resource.getBytes(), "directory exact source bytes");
            bytes(resource.getBytes(), zipped.getBytes(), "ZIP exact source bytes");
        }
        equal("data/fixture/tags/worldgen/biome/nested/wet.json",
                fromZip.getResource("tags/worldgen/biome", "fixture:nested/wet").getPath(), "nested tag registry");
        equal("data/fixture/worldgen/configured_feature/nested/tree.json",
                fromZip.getResource("worldgen/configured_feature", "fixture:nested/tree").getPath(), "nested worldgen registry");
        equal("minecraft:a", fromZip.getResource("function", "a").getId(), "default lookup namespace");
        equal(2, fromZip.resources("function").size(), "typed resource list");
        equal(0, fromZip.resources("absent").size(), "absent type list");
        check(fromZip.getResource("function", "fixture:absent") == null, "absent resource");
        equal(fromDirectory.getAssets().keySet(), fromZip.getAssets().keySet(), "asset path parity");
        for (String path : fromDirectory.getAssets().keySet()) {
            bytes(files.get(path), fromDirectory.getAssets().get(path), "asset exact bytes");
            bytes(files.get(path), fromZip.getAssets().get(path), "ZIP asset exact bytes");
        }
        immutable(() -> fromDirectory.getResources().clear());
        immutable(() -> fromDirectory.resources("function").clear());
        immutable(() -> fromDirectory.getConstants().clear());
        immutable(() -> fromDirectory.getDependencies().clear());
        immutable(() -> fromDirectory.getExternalFunctions().clear());
        immutable(() -> fromDirectory.getAssets().clear());
        SourceResource function = fromZip.getResource("function", "fixture:nested/run");
        byte[] returned = function.getBytes();
        returned[0] = 0;
        bytes(files.get(function.getPath()), function.getBytes(), "defensive resource bytes");
        String assetPath = "assets/Custom Namespace/Textures/\u4e2d.bin";
        fromZip.getAssets().get(assetPath)[0] = 99;
        bytes(files.get(assetPath), fromZip.getAssets().get(assetPath), "defensive asset arrays");
        SourceResource json = fromZip.getResource("custom_registry", "fixture:nested/a");
        json.getJson().getAsJsonObject().addProperty("never", "mutated");
        equal("dropped", json.getJson().getAsJsonObject().get("never").getAsString(), "fresh resource JSON");
        Files.delete(archive);
        Files.write(directory.resolve(function.getPath()), new byte[] { 0 });
        bytes(files.get(function.getPath()), fromDirectory.getResource("function", function.getId()).getBytes(), "detached directory snapshot");
        bytes(files.get(function.getPath()), function.getBytes(), "detached closed ZIP snapshot");
    }

    private static void defaults(Path temporary) throws Exception {
        Path input = temporary.resolve("defaults");
        directory(input, manifest(MINIMAL));
        Project project = ProjectReader.read(input);
        equal("fixture", project.getName(), "default name");
        equal("1.0", project.getVersion(), "default version");
        equal("1.12.2", project.getMinecraftVersion(), "default target");
        equal("Fixture", project.getDescription(), "plain description");
        check(project.getResources().isEmpty() && project.getAssets().isEmpty(), "metadata-only project");
        check(project.getConstants().isEmpty() && project.getDependencies().isEmpty()
                && project.getExternalFunctions().isEmpty(), "empty metadata collections");
        Path arrayInput = temporary.resolve("description-array");
        directory(arrayInput, manifest(MINIMAL.replace("\"Fixture\"", "[{\"text\":\"a\"},\"b\"]")));
        equal("[{\"text\":\"a\"},\"b\"]", ProjectReader.read(arrayInput).getDescription(), "array text component");
    }

    private static void metadataFailures(Path temporary) throws Exception {
        List<String> invalid = Arrays.asList(
                "{}", "[]", "null", "{", MINIMAL + " {}",
                MINIMAL.replace("\"id\":\"fixture\"", "\"id\":\"fixture\",\"id\":\"other\""),
                MINIMAL.replace("\"id\":\"fixture\"", "\"id\":\"Upper\""),
                MINIMAL.replace("\"id\":\"fixture\"", "\"id\":\"bad:path\""),
                MINIMAL.replace("\"id\":\"fixture\"", "\"id\":3"),
                MINIMAL.replace("\"id\":\"fixture\"", "\"id\":\"\""),
                MINIMAL.replace("\"pack_format\":121", "\"pack_format\":1.5"),
                MINIMAL.replace("\"pack_format\":121", "\"pack_format\":0"),
                MINIMAL.replace("\"pack_format\":121", "\"pack_format\":2147483648"),
                MINIMAL.replace("\"pack_format\":121", "\"pack_format\":\"121\""),
                MINIMAL.replace("\"description\":\"Fixture\"", "\"description\":null"),
                MINIMAL.replace("\"description\":\"Fixture\"", "\"description\":true"),
                withRml("\"language_version\":2"), withRml("\"language_version\":1.5"),
                withRml("\"minecraft\":\"26.3\""), withRml("\"version\":1"), withRml("\"name\":null"),
                withRml("\"constants\":[]"), withRml("\"constants\":{\"x\":{}}"),
                withRml("\"constants\":{\"x\":null}"), withRml("\"constants\":{\"x\":\"a\\nb\"}"),
                withRml("\"constants\":{\"x\":\"a\\rb\"}"),
                withRml("\"dependencies\":\"after:forge\""), withRml("\"dependencies\":[3]"),
                withRml("\"external_functions\":[\"Bad:fn\"]"), withRml("\"external_functions\":[\"x:../fn\"]"),
                MINIMAL.replace("\"pack_format\"", "pack_format"), MINIMAL.replace("121", "121 /* comment */"));
        int index = 0;
        for (String metadata : invalid) {
            Path input = temporary.resolve("metadata-error-" + index++);
            directory(input, manifest(metadata));
            failure("invalid_metadata", "pack.mcmeta", () -> ProjectReader.read(input));
        }
        Path missing = Files.createDirectory(temporary.resolve("missing-manifest"));
        failure("invalid_metadata", "pack.mcmeta", () -> ProjectReader.read(missing));
    }

    private static void resourceFailures(Path temporary) throws Exception {
        String[] malformed = {
                "data/fixture/Upper/a.json", "data/Upper/recipe/a.json", "data/fixture/recipe/Upper.json",
                "data/fixture/recipe/.json", "data/fixture/recipe.json", "data/fixture/tags/item.json",
                "data/fixture/tags/worldgen/biome.json", "data/fixture/worldgen/biome.json",
                "data/fixture/function/a.json", "data/fixture/structure/a.json", "data/fixture/recipe/a.txt",
                "data/fixture/unknown/a.bin"
        };
        for (int i = 0; i < malformed.length; i++) {
            Map<String, byte[]> files = manifest(MINIMAL);
            String path = malformed[i];
            put(files, path, "{}");
            Path input = temporary.resolve("resource-error-" + i);
            directory(input, files);
            try {
                ProjectReader.read(input);
                throw new AssertionError("Accepted malformed resource " + path);
            } catch (CompileException expected) {
                equal(path, expected.getSource(), "resource error source");
                equal(0, expected.getLine(), "resource error unavailable line");
                check(expected.getCode().equals("invalid_identifier") || expected.getCode().equals("invalid_resource"), "resource error code");
            }
        }
        Map<String, byte[]> files = manifest(MINIMAL);
        put(files, "data/fixture/predicate/broken.json", "{bad");
        Path broken = temporary.resolve("resource-json-error");
        directory(broken, files);
        SourceResource resource = ProjectReader.read(broken).getResource("predicate", "fixture:broken");
        failure("invalid_json", resource.getPath(), resource::getJson);
    }

    private static void archiveFailures(Path temporary) throws Exception {
        Map<String, byte[]> duplicate = manifest(MINIMAL);
        put(duplicate, "data/fixture/function/a.mcfunction", "say a");
        put(duplicate, "data/fixture/function/./a.mcfunction", "say duplicate");
        Path input = temporary.resolve("duplicate.zip");
        archive(input, duplicate);
        ioFailure(() -> ProjectReader.read(input));
        Map<String, byte[]> unsafe = manifest(MINIMAL);
        put(unsafe, "../outside.json", "{}");
        Path unsafeInput = temporary.resolve("unsafe.zip");
        archive(unsafeInput, unsafe);
        ioFailure(() -> ProjectReader.read(unsafeInput));
    }

    private static String withRml(String field) {
        return MINIMAL.replace("\"id\":\"fixture\"", "\"id\":\"fixture\"," + field);
    }

    private static Map<String, byte[]> manifest(String metadata) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        put(files, "pack.mcmeta", metadata);
        return files;
    }

    private static void put(Map<String, byte[]> files, String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void directory(Path directory, Map<String, byte[]> files) throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path path = directory.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, entry.getValue());
        }
    }

    private static void archive(Path archive, Map<String, byte[]> files) throws IOException {
        List<String> paths = new ArrayList<>(files.keySet());
        Collections.reverse(paths);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String path : paths) {
                zip.putNextEntry(new ZipEntry(path));
                zip.write(files.get(path));
                zip.closeEntry();
            }
        }
    }

    private static void failure(String code, String source, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + code + " at " + source);
        } catch (CompileException expected) {
            equal(code, expected.getCode(), "diagnostic code");
            equal(source, expected.getSource(), "diagnostic source");
            equal(0, expected.getLine(), "unavailable JSON line");
        }
    }

    private static void immutable(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Mutable project collection");
        } catch (UnsupportedOperationException expected) {
            // Required immutable collection contract.
        }
    }

    private static void ioFailure(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Accepted unsafe or duplicate archive");
        } catch (IOException expected) {
            check(expected.getMessage() != null, "archive failure carries path context");
        }
    }

    private static void bytes(byte[] expected, byte[] actual, String message) {
        check(Arrays.equals(expected, actual), message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected == null ? actual == null : expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action {
        void run() throws Exception;
    }
}
