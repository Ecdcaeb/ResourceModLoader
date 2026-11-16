package rml.loader.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.runtime.ModuleLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Reads the compiled RML protocol. Reading never registers game content. */
public final class ContentCatalog {
    public static final ResourceLocation MODULE = new ResourceLocation("rml", "content");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final ModuleLoader modules;
    private volatile Snapshot snapshot = new Snapshot(Collections.emptyMap());

    public ContentCatalog(ModuleLoader modules) { this.modules = modules; }

    public Snapshot read() { return read(null); }

    public Snapshot read(String packId) {
        Map<String, List<CompiledResource>> byPack = new TreeMap<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ContainerHolder.ModuleConsumeContext source : modules.read(ModuleType.valueOf(MODULE), ContentCatalog.class, packId)) {
            if (!source.isExtension("json")) {
                throw new IllegalArgumentException("Compiled content must be JSON: " + source.getUri());
            }
            try {
                String pack = source.getContainerHolder().getContainer().getModId();
                CompiledResource resource = decode(pack, source.getUri().toString(), source.toJson().getAsJsonObject());
                String key = pack + "|" + resource.getType() + "|" + resource.getId();
                if (seen.put(key, Boolean.TRUE) != null) throw new IllegalArgumentException("Duplicate compiled content: " + key);
                byPack.computeIfAbsent(pack, ignored -> new ArrayList<>()).add(resource);
            } catch (IOException | RuntimeException | rml.deserializer.JsonDeserializeException e) {
                throw new IllegalArgumentException("Invalid compiled content at " + source.getUri() + ": " + e.getMessage(), e);
            }
        }
        return new Snapshot(byPack);
    }

    public void reload() { snapshot = read(); }
    public Snapshot snapshot() { return snapshot; }

    /** Shared decoder for discovered packs and detached compiler archives. */
    static CompiledResource decode(String pack, String source, JsonObject object) {
        if (!object.has("format") || !"1".equals(object.get("format").getAsString()))
            throw new IllegalArgumentException("Unsupported compiled content format");
        String type = object.get("type").getAsString();
        String name = object.get("id").getAsString();
        JsonElement value = object.get("value");
        if (type.isEmpty() || !IDENTIFIER.matcher(name).matches() || value == null || value.isJsonNull())
            throw new IllegalArgumentException("Invalid compiled content identity or value");
        return new CompiledResource(pack, type, new ResourceLocation(name), source, value);
    }

    public static Snapshot compiledSnapshot(String pack, List<CompiledResource> resources) {
        return new Snapshot(Collections.singletonMap(pack, resources));
    }

    public static final class Snapshot {
        private final Map<String, List<CompiledResource>> byPack;
        private final Map<String, Map<ResourceLocation, CompiledResource>> byType;

        private Snapshot(Map<String, List<CompiledResource>> source) {
            Map<String, List<CompiledResource>> packs = new TreeMap<>();
            Map<String, Map<ResourceLocation, CompiledResource>> types = new TreeMap<>();
            for (Map.Entry<String, List<CompiledResource>> entry : source.entrySet()) {
                List<CompiledResource> resources = new ArrayList<>(entry.getValue());
                resources.sort((left, right) -> (left.getType() + "|" + left.getId()).compareTo(right.getType() + "|" + right.getId()));
                packs.put(entry.getKey(), Collections.unmodifiableList(resources));
                for (CompiledResource resource : resources) {
                    Map<ResourceLocation, CompiledResource> values = types.get(resource.getType());
                    if (values == null) {
                        values = new TreeMap<>();
                        types.put(resource.getType(), values);
                    }
                    values.put(resource.getId(), resource);
                }
            }
            for (Map.Entry<String, Map<ResourceLocation, CompiledResource>> entry : types.entrySet()) {
                entry.setValue(Collections.unmodifiableMap(entry.getValue()));
            }
            byPack = Collections.unmodifiableMap(packs);
            byType = Collections.unmodifiableMap(types);
        }

        public Map<String, List<CompiledResource>> getPacks() { return byPack; }
        public List<CompiledResource> resourcesForPack(String pack) {
            List<CompiledResource> result = byPack.get(pack);
            return result == null ? Collections.emptyList() : result;
        }
        public Map<ResourceLocation, CompiledResource> resources(String type) {
            Map<ResourceLocation, CompiledResource> result = byType.get(type);
            return result == null ? Collections.emptyMap() : result;
        }
    }
}
