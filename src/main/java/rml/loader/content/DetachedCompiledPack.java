package rml.loader.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;

/** Immutable compiler input, decoded without Forge containers or registry mutations. */
public final class DetachedCompiledPack {
    public static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
    private final String id;
    private final String version;
    private final Set<String> dependencies;
    private final ContentCatalog.Snapshot content;

    private DetachedCompiledPack(String id, String version, Set<String> dependencies, List<CompiledResource> resources) {
        this.id = id;
        this.version = version;
        this.dependencies = Collections.unmodifiableSet(dependencies);
        this.content = ContentCatalog.compiledSnapshot(id, resources);
    }

    public String getId() { return id; }
    public String getVersion() { return version; }
    public Set<String> getDependencies() { return dependencies; }
    public ContentCatalog.Snapshot getContent() { return content; }

    /** Accepts exactly the compiler's content module; installation cannot smuggle executable modules. */
    public static DetachedCompiledPack read(byte[] archive, String expectedPack) throws IOException {
        if (archive.length > MAX_ARCHIVE_BYTES) throw new IOException("Compiled archive exceeds size limit");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        byte[] buffer = new byte[8192];
        // ZipFile validates the central directory used by discovery on the next launch.
        Path snapshot = Files.createTempFile("rml-compiled-", ".zip");
        try {
            Files.write(snapshot, archive);
            try (ZipFile zip = new ZipFile(snapshot.toFile(), StandardCharsets.UTF_8)) {
                Enumeration<? extends ZipEntry> members = zip.entries();
                while (members.hasMoreElements()) {
                    ZipEntry entry = members.nextElement();
                    String name = entry.getName();
                    if (names.size() >= 8192 || !safeEntry(name) || !names.add(name))
                        throw new IOException("Invalid or duplicate archive path: " + name);
                    if (entry.isDirectory()) continue;
                    boolean keep = name.equals("rml.info") || name.startsWith("assets/" + expectedPack + "/content/");
                    ByteArrayOutputStream data = keep ? new ByteArrayOutputStream() : null;
                    CRC32 crc = new CRC32();
                    int size = 0;
                    try (InputStream input = zip.getInputStream(entry)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            expanded += count;
                            size += count;
                            if (size > 16 * 1024 * 1024 || expanded > 128L * 1024 * 1024)
                                throw new IOException("Expanded archive exceeds size limit");
                            crc.update(buffer, 0, count);
                            if (data != null) data.write(buffer, 0, count);
                        }
                    }
                    if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Corrupt archive entry: " + name);
                    entries.put(name, data == null ? null : data.toByteArray());
                }
            }
        } finally {
            Files.deleteIfExists(snapshot);
        }
        try {
            byte[] info = entries.get("rml.info");
            if (info == null) throw new IllegalArgumentException("Missing rml.info");
            JsonArray packs = json(info).getAsJsonArray();
            if (packs.size() != 1) throw new IllegalArgumentException("Archive must contain exactly one pack");
            JsonObject metadata = packs.get(0).getAsJsonObject();
            String id = metadata.get("modid").getAsString();
            if (!expectedPack.equals(id)) throw new IllegalArgumentException("Archive pack does not belong to this session");
            if (metadata.get("pack_version").getAsInt() != 3 || !"1.12.2".equals(metadata.get("mcversion").getAsString()))
                throw new IllegalArgumentException("Unsupported pack or Minecraft version");
            String version = metadata.get("version").getAsString();
            if (version.isEmpty() || metadata.get("name").getAsString().isEmpty())
                throw new IllegalArgumentException("Missing pack name or version");
            JsonArray modules = metadata.getAsJsonArray("modules");
            if (modules.size() != 1) throw new IllegalArgumentException("Expected one compiled content module");
            JsonObject module = modules.get(0).getAsJsonObject();
            if (!"rml:content".equals(module.get("name").getAsString())
                    || !"content".equals(module.get("location").getAsString())
                    || !module.get("forceLoaded").getAsBoolean())
                throw new IllegalArgumentException("Unsupported compiled module");
            // Runtime dependencies are module "requires"; metadata dependencies belong to Forge discovery.
            Set<String> dependencies = new LinkedHashSet<>();
            if (module.has("requires")) {
                for (JsonElement dependency : module.getAsJsonArray("requires")) {
                    String value = dependency.getAsString();
                    if (value.isEmpty() || !dependencies.add(value))
                        throw new IllegalArgumentException("Invalid or duplicate module dependency");
                }
            }
            String prefix = "assets/" + id + "/content/";
            List<CompiledResource> resources = new ArrayList<>();
            Set<String> identities = new HashSet<>();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith(prefix)) {
                    if (!name.endsWith(".json")) throw new IllegalArgumentException("Compiled content must be JSON");
                    CompiledResource resource = ContentCatalog.decode(id, name, json(entry.getValue()).getAsJsonObject());
                    String key = resource.getType() + "/" + resource.getId();
                    if (!identities.add(key)) throw new IllegalArgumentException("Duplicate compiled content: " + key);
                    resources.add(resource);
                } else if (!name.equals("rml.info") && !name.equals("pack.mcmeta")
                        && !name.equals("META-INF/rml/source-map.json") && !name.startsWith("assets/")) {
                    throw new IllegalArgumentException("Unexpected compiler archive entry: " + name);
                } else if (name.endsWith(".class") || name.endsWith(".jar") || name.contains("/content/")) {
                    throw new IllegalArgumentException("Unexpected executable or foreign content entry: " + name);
                }
            }
            return new DetachedCompiledPack(id, version, dependencies, resources);
        } catch (RuntimeException error) {
            throw new IOException("Invalid compiled archive: " + error.getMessage(), error);
        }
    }

    private static JsonElement json(byte[] data) {
        return new JsonParser().parse(new String(data, StandardCharsets.UTF_8));
    }

    private static boolean safeEntry(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0)
            return false;
        String value = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (String part : value.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }
}
