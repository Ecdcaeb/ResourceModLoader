package rml.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** End-to-end CLI publication contract, including preservation of a previously valid build. */
public final class CompilerContractTest {
    public static void main(String[] args) throws Exception {
        Path workspace = Files.createTempDirectory("rml-compiler-contract-");
        try {
            Path source = workspace.resolve("source");
            put(source, "pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"Compiler publication contract\"},\"rml\":{\"id\":\"demo\",\"version\":\"1.0\"}}");
            put(source, "data/demo/function/start.mcfunction", "function demo:child\n");
            put(source, "data/demo/function/child.mcfunction", "say Ready\n");
            put(source, "data/minecraft/tags/function/load.json", "{\"values\":[\"demo:start\"]}");
            put(source, "data/demo/context_int_provider/amount.json", "{\"type\":\"minecraft:constant\",\"value\":3}");
            put(source, "data/demo/worldgen/density_function/ground.json", "0.5");
            Path destination = workspace.resolve("compiled.zip");
            check(run("compile", source.toString(), destination.toString()) == 0, "Valid source graph must produce an artifact");
            try (ZipFile zip = new ZipFile(destination.toFile())) {
                ZipEntry manifest = zip.getEntry("rml.info");
                check(manifest != null, "Published artifact must be installable as an RML mod");
                JsonObject pack = new JsonParser().parse(new String(read(zip, manifest), StandardCharsets.UTF_8)).getAsJsonArray().get(0).getAsJsonObject();
                check("demo".equals(pack.get("modid").getAsString()), "Published mod identity must match the source namespace");
                check(zip.getEntry("assets/demo/content/function/demo/start.json") != null
                        && zip.getEntry("assets/demo/content/context_int_provider/demo/amount.json") != null
                        && zip.getEntry("assets/demo/content/worldgen/density_function/demo/ground.json") != null,
                        "One artifact must include functions and native registry definitions");
            }
            byte[] published = Files.readAllBytes(destination);
            check(run("check", source.toString()) == 0 && Arrays.equals(published, Files.readAllBytes(destination)),
                    "Checking a project must preserve the published build");
            put(source, "data/demo/function/start.mcfunction", "function demo:missing\n");
            check(run("compile", source.toString(), destination.toString()) == 1, "Broken linked graph must reject publication");
            check(Arrays.equals(published, Files.readAllBytes(destination)), "Failed compilation must preserve the complete prior ZIP");
            Path original = workspace.resolve("source.zip");
            Files.write(original, published);
            check(run("compile", original.toString(), original.toString()) == 1
                            && Arrays.equals(published, Files.readAllBytes(original)),
                    "The CLI must reject replacing its own input archive before writing");
            System.out.println("CompilerContractTest passed");
        } finally {
            Files.walkFileTree(workspace, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    Files.delete(path); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(path); return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static int run(String... args) throws Exception {
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(messages, true, "UTF-8")) {
            return Main.run(args, stream, stream);
        }
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (java.io.InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private static void put(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
