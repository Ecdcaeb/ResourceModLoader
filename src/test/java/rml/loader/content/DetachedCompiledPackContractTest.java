package rml.loader.content;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Real ZIP/compiler envelope regression cases, shared with the native import contract. */
public final class DetachedCompiledPackContractTest {
    public static final String PACK = "rml_agent_0123456789abcdef0123456789abcdef";

    public static void main(String[] args) throws Exception {
        byte[] valid = archive(PACK, "tag/item", "fixture:blocks", "[\"minecraft:stone\"]");
        DetachedCompiledPack pack = DetachedCompiledPack.read(valid, PACK);
        check(pack.getContent().resourcesForPack(PACK).size() == 1, "Compiled resource decoded");
        check(pack.getDependencies().isEmpty(), "Forge metadata ordering is not a runtime module dependency");
        rejects(() -> DetachedCompiledPack.read(valid, PACK + "other"));
        rejects(() -> DetachedCompiledPack.read(Arrays.copyOf(valid, valid.length - 22), PACK));
        Map<String, byte[]> duplicate = entries(PACK, "tag/item", "fixture:blocks", "[]");
        duplicate.put("assets/" + PACK + "/content/duplicate.json", envelope("tag/item", "fixture:blocks", "[]"));
        rejects(() -> DetachedCompiledPack.read(zip(duplicate), PACK));
        Map<String, byte[]> traversal = entries(PACK, "tag/item", "fixture:blocks", "[]");
        traversal.put("../rml.info", new byte[0]);
        rejects(() -> DetachedCompiledPack.read(zip(traversal), PACK));
        Map<String, byte[]> executable = entries(PACK, "tag/item", "fixture:blocks", "[]");
        executable.put("Injected.class", new byte[0]);
        rejects(() -> DetachedCompiledPack.read(zip(executable), PACK));
        Map<String, byte[]> module = entries(PACK, "tag/item", "fixture:blocks", "[]");
        module.put("rml.info", new String(module.get("rml.info"), StandardCharsets.UTF_8).replace("rml:content", "rml:functions").getBytes(StandardCharsets.UTF_8));
        rejects(() -> DetachedCompiledPack.read(zip(module), PACK));
        System.out.println("DetachedCompiledPackContractTest passed");
    }

    public static byte[] archive(String pack, String type, String id, String value) throws IOException {
        return zip(entries(pack, type, id, value));
    }

    private static Map<String, byte[]> entries(String pack, String type, String id, String value) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("rml.info", ("[{\"pack_version\":3,\"modid\":\"" + pack + "\",\"name\":\"Contract\",\"version\":\"1\","
                + "\"mcversion\":\"1.12.2\",\"useDependencyInformation\":true,\"dependencies\":[\"required-after:forge@[14.23.5,)\"],"
                + "\"modules\":[{\"name\":\"rml:content\",\"location\":\"content\",\"forceLoaded\":true}]}]").getBytes(StandardCharsets.UTF_8));
        entries.put("assets/" + pack + "/content/" + type + "/" + id.replace(':', '/') + ".json", envelope(type, id, value));
        return entries;
    }

    private static byte[] envelope(String type, String id, String value) {
        return ("{\"format\":1,\"type\":\"" + type + "\",\"id\":\"" + id + "\",\"value\":" + value + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void rejects(Checked operation) throws Exception {
        try { operation.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected invalid archive rejection");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Checked { void run() throws Exception; }
}
