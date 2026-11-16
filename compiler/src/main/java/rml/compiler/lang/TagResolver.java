package rml.compiler.lang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rml.compiler.CompileException;
import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.compiler.source.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Resolves one registry's tags without depending on a game registry. */
public final class TagResolver {
    private TagResolver() { }

    public static Map<String, List<String>> resolve(Project project, String registry, Set<String> knownEntries) {
        String normalizedRegistry;
        try {
            normalizedRegistry = Ids.path(Ids.parse("minecraft:" + registry));
        } catch (IllegalArgumentException e) {
            throw new CompileException("invalid_registry", "pack.mcmeta", 0, "Invalid tag registry: " + registry, e);
        }
        Map<String, SourceResource> tags = new TreeMap<>();
        for (SourceResource resource : project.resources("tags/" + normalizedRegistry)) {
            tags.put(resource.getId(), resource);
        }
        Set<String> known = null;
        if (knownEntries != null) {
            known = new LinkedHashSet<>();
            for (String entry : knownEntries) known.add(Ids.parse(entry));
        }
        Resolver resolver = new Resolver(tags, known);
        for (String id : tags.keySet()) resolver.resolve(id, tags.get(id));
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String id : tags.keySet()) result.put(id, resolver.resolved.get(id));
        return Collections.unmodifiableMap(result);
    }

    private static final class Resolver {
        private final Map<String, SourceResource> tags;
        private final Set<String> known;
        private final Map<String, List<String>> resolved = new LinkedHashMap<>();
        private final Set<String> visiting = new LinkedHashSet<>();

        private Resolver(Map<String, SourceResource> tags, Set<String> known) {
            this.tags = tags;
            this.known = known;
        }

        private List<String> resolve(String id, SourceResource referringSource) {
            List<String> cached = resolved.get(id);
            if (cached != null) return cached;
            if (!visiting.add(id)) {
                throw error("tag_cycle", referringSource, "Tag cycle: " + visiting + " -> " + id);
            }
            SourceResource source = tags.get(id);
            JsonElement root = source.getJson();
            if (root == null || !root.isJsonObject()) throw error("invalid_tag", source, "Tag must be an object");
            JsonObject object = root.getAsJsonObject();
            checkFields(object, source, "replace", "values");
            if (object.has("replace") && !isBoolean(object.get("replace"))) {
                throw error("invalid_tag", source, "replace must be a boolean");
            }
            JsonElement values = object.get("values");
            if (values == null || !values.isJsonArray()) throw error("invalid_tag", source, "values must be an array");
            JsonArray array = values.getAsJsonArray();
            LinkedHashSet<String> entries = new LinkedHashSet<>();
            for (JsonElement value : array) {
                boolean required = true;
                String reference;
                if (isString(value)) {
                    reference = value.getAsString();
                } else if (value.isJsonObject()) {
                    JsonObject entry = value.getAsJsonObject();
                    checkFields(entry, source, "id", "required");
                    if (!isString(entry.get("id"))) throw error("invalid_tag", source, "Tag entry id must be a string");
                    reference = entry.get("id").getAsString();
                    if (entry.has("required")) {
                        if (!isBoolean(entry.get("required"))) throw error("invalid_tag", source, "required must be a boolean");
                        required = entry.get("required").getAsBoolean();
                    }
                } else {
                    throw error("invalid_tag", source, "Tag entries must be strings or {id, required} objects");
                }
                boolean nested = reference.startsWith("#");
                String entryId;
                try {
                    entryId = Ids.parse(nested ? reference.substring(1) : reference);
                } catch (IllegalArgumentException e) {
                    throw new CompileException("invalid_identifier", source.getPath(), 0, "Invalid tag entry: " + reference, e);
                }
                if (nested) {
                    if (tags.containsKey(entryId)) entries.addAll(resolve(entryId, source));
                    else if (required) throw error("missing_tag", source, "Missing required tag: " + entryId);
                } else if (known == null || known.contains(entryId)) {
                    entries.add(entryId);
                } else if (required) {
                    throw error("missing_tag_entry", source, "Missing required tag entry: " + entryId);
                }
            }
            visiting.remove(id);
            List<String> result = Collections.unmodifiableList(new ArrayList<>(entries));
            resolved.put(id, result);
            return result;
        }
    }

    private static void checkFields(JsonObject object, SourceResource source, String... allowed) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            boolean found = false;
            for (String field : allowed) if (field.equals(entry.getKey())) found = true;
            if (!found) throw error("invalid_tag", source, "Unknown tag field: " + entry.getKey());
        }
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static CompileException error(String code, SourceResource source, String message) {
        return new CompileException(code, source.getPath(), 0, message);
    }
}
