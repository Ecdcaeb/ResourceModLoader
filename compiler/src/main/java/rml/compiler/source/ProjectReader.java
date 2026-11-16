package rml.compiler.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import rml.compiler.CompileException;
import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.io.PackFile;
import rml.io.PackSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads directory and ZIP inputs through the same detached PackSource snapshots. */
public final class ProjectReader {
    private static final String MANIFEST = "pack.mcmeta";

    private ProjectReader() { }

    public static Project read(Path input) throws IOException {
        try (PackSource source = PackSource.open(input)) {
            List<PackFile> manifests = source.read(MANIFEST, false);
            if (manifests.isEmpty()) throw metadata("Missing pack.mcmeta");
            JsonObject root = manifest(manifests.get(0));
            JsonObject pack = object(root, "pack");
            positiveInteger(pack, "pack_format", null);
            JsonElement description = pack.get("description");
            if (description == null || description.isJsonNull()
                    || description.isJsonPrimitive() && !description.getAsJsonPrimitive().isString()) {
                throw metadata("pack.description must be a string or JSON text component object/array");
            }
            JsonObject rml = object(root, "rml");
            int language = positiveInteger(rml, "language_version", 1);
            if (language != 1) throw metadata("Unsupported rml.language_version: " + language);
            String id = string(rml, "id", null);
            try {
                if (!Ids.parse(id + ":probe").equals(id + ":probe")) {
                    throw new IllegalArgumentException("Invalid namespace");
                }
            } catch (IllegalArgumentException e) {
                throw metadata("rml.id must be a lower-case resource namespace: " + id);
            }
            String name = string(rml, "name", id);
            String version = string(rml, "version", "1.0");
            String minecraft = string(rml, "minecraft", "1.12.2");
            if (!"1.12.2".equals(minecraft)) throw metadata("Unsupported Minecraft target: " + minecraft);
            Map<String, String> constants = constants(rml);
            List<String> dependencies = strings(rml, "dependencies");
            Set<String> externalFunctions = new LinkedHashSet<>();
            for (String external : strings(rml, "external_functions")) {
                try {
                    externalFunctions.add(Ids.parse(external));
                } catch (IllegalArgumentException e) {
                    throw metadata("Invalid rml.external_functions identifier: " + external);
                }
            }
            Map<String, SourceResource> resources = new TreeMap<>();
            for (PackFile file : source.read("data", true)) index(file, resources);
            Map<String, PackFile> assets = new TreeMap<>();
            for (PackFile file : source.read("assets", true)) assets.put(file.getPath(), file);
            return new Project(id, name, version, minecraft,
                    description.isJsonPrimitive() ? description.getAsString() : description.toString(),
                    constants, dependencies, externalFunctions, resources, assets);
        }
    }

    private static void index(PackFile file, Map<String, SourceResource> resources) {
        String path = file.getPath();
        String[] segments = path.split("/", -1);
        if (segments.length < 4) throw resource(path, "Expected data/<namespace>/<type>/<path>");
        int contentStart = 3;
        String type = segments[2];
        if ("worldgen".equals(type)) {
            if (segments.length < 5) throw resource(path, "A worldgen resource requires a registry and path");
            type += "/" + segments[3];
            contentStart = 4;
        } else if ("tags".equals(type)) {
            if (segments.length < 5) throw resource(path, "A tag requires a registry and path");
            type += "/" + segments[3];
            contentStart = 4;
            if ("worldgen".equals(segments[3])) {
                if (segments.length < 6) throw resource(path, "A worldgen tag requires a registry and path");
                type += "/" + segments[4];
                contentStart = 5;
            }
        }
        String extension = "function".equals(type) ? ".mcfunction"
                : "structure".equals(type) ? ".nbt" : ".json";
        if (!path.endsWith(extension)) {
            throw resource(path, "Resource type " + type + " requires " + extension + "; no data files are ignored");
        }
        StringBuilder resourcePath = new StringBuilder();
        for (int i = contentStart; i < segments.length; i++) {
            if (resourcePath.length() != 0) resourcePath.append('/');
            resourcePath.append(segments[i]);
        }
        resourcePath.setLength(resourcePath.length() - extension.length());
        String id;
        try {
            Ids.parse("minecraft:" + type);
            id = Ids.parse(segments[1] + ":" + resourcePath);
        } catch (IllegalArgumentException e) {
            throw new CompileException("invalid_identifier", path, 0, e.getMessage(), e);
        }
        String key = type + "|" + id;
        SourceResource previous = resources.get(key);
        if (previous != null) {
            throw new CompileException("duplicate_resource", path, 0,
                    "Duplicate " + key + "; first declared at " + previous.getPath());
        }
        resources.put(key, SourceResource.fromSnapshot(type, id, file));
    }

    private static Map<String, String> constants(JsonObject rml) {
        Map<String, String> result = new TreeMap<>();
        if (!rml.has("constants")) return result;
        JsonObject constants = object(rml, "constants");
        for (Map.Entry<String, JsonElement> entry : constants.entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().isEmpty() || !value.isJsonPrimitive()) {
                throw metadata("rml.constants must contain named string, number or boolean values");
            }
            String text = value.getAsString();
            if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                    || text.indexOf('\u0085') >= 0 || text.indexOf('\u2028') >= 0 || text.indexOf('\u2029') >= 0) {
                throw metadata("rml.constants." + entry.getKey() + " must be a single-line value");
            }
            result.put(entry.getKey(), text);
        }
        return result;
    }

    private static List<String> strings(JsonObject parent, String key) {
        List<String> result = new ArrayList<>();
        if (!parent.has(key)) return result;
        JsonElement value = parent.get(key);
        if (!value.isJsonArray()) throw metadata("rml." + key + " must be an array of strings");
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || element.getAsString().trim().isEmpty()
                    || element.getAsString().indexOf('\n') >= 0 || element.getAsString().indexOf('\r') >= 0) {
                throw metadata("rml." + key + " must contain nonempty single-line strings");
            }
            result.add(element.getAsString());
        }
        return result;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw metadata(key + " must be an object");
        return value.getAsJsonObject();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement value = parent.get(key);
        if (value == null && fallback != null) return fallback;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().trim().isEmpty()
                || value.getAsString().indexOf('\n') >= 0 || value.getAsString().indexOf('\r') >= 0) {
            throw metadata("rml." + key + " must be a nonempty single-line string");
        }
        return value.getAsString();
    }

    private static int positiveInteger(JsonObject parent, String key, Integer fallback) {
        JsonElement value = parent.get(key);
        if (value == null && fallback != null) return fallback;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw metadata(key + " must be a positive integer");
        }
        try {
            int integer = value.getAsBigDecimal().intValueExact();
            if (integer > 0) return integer;
        } catch (ArithmeticException | NumberFormatException ignored) {
            // The diagnostic below covers fractions, overflow and malformed numbers.
        }
        throw metadata(key + " must be a positive integer");
    }

    private static JsonObject manifest(PackFile file) {
        try (JsonReader reader = new JsonReader(file.openReader(StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            JsonElement value = json(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !value.isJsonObject()) {
                throw metadata("pack.mcmeta must contain exactly one JSON object");
            }
            return value.getAsJsonObject();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new CompileException("invalid_metadata", MANIFEST, 0, "Invalid pack.mcmeta JSON: " + e.getMessage(), e);
        }
    }

    /** Gson's JsonParser enables lenient parsing; keep the manifest strict and detect duplicate keys. */
    private static JsonElement json(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw metadata("Duplicate metadata field: " + name);
                    object.add(name, json(reader));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(json(reader));
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw metadata("Expected a JSON value in pack.mcmeta");
        }
    }

    private static CompileException metadata(String message) {
        return new CompileException("invalid_metadata", MANIFEST, 0, message);
    }

    private static CompileException resource(String path, String message) {
        return new CompileException("invalid_resource", path, 0, message);
    }
}
