package rml.compiler.source;

import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.io.PackFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Immutable metadata and detached source snapshots for one enhanced datapack. */
public final class Project {
    private final String id;
    private final String name;
    private final String version;
    private final String minecraftVersion;
    private final String description;
    private final Map<String, String> constants;
    private final List<String> dependencies;
    private final Set<String> externalFunctions;
    private final Map<String, SourceResource> resources;
    private final Map<String, List<SourceResource>> resourcesByType;
    private final Map<String, PackFile> assets;

    Project(String id, String name, String version, String minecraftVersion, String description,
            Map<String, String> constants, List<String> dependencies, Set<String> externalFunctions,
            Map<String, SourceResource> resources, Map<String, PackFile> assets) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.minecraftVersion = minecraftVersion;
        this.description = description;
        this.constants = Collections.unmodifiableMap(new TreeMap<>(constants));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        this.externalFunctions = Collections.unmodifiableSet(new LinkedHashSet<>(externalFunctions));
        this.resources = Collections.unmodifiableMap(new TreeMap<>(resources));
        Map<String, List<SourceResource>> byType = new TreeMap<>();
        for (SourceResource resource : this.resources.values()) {
            List<SourceResource> entries = byType.get(resource.getType());
            if (entries == null) {
                entries = new ArrayList<>();
                byType.put(resource.getType(), entries);
            }
            entries.add(resource);
        }
        for (Map.Entry<String, List<SourceResource>> entry : byType.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.resourcesByType = Collections.unmodifiableMap(byType);
        this.assets = Collections.unmodifiableMap(new TreeMap<>(assets));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getDescription() { return description; }
    public Map<String, String> getConstants() { return constants; }
    public List<String> getDependencies() { return dependencies; }
    public Set<String> getExternalFunctions() { return externalFunctions; }
    public Map<String, SourceResource> getResources() { return resources; }

    /** A fresh defensive byte array for every asset, ordered by its exact pack-relative path. */
    public Map<String, byte[]> getAssets() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, PackFile> asset : assets.entrySet()) {
            result.put(asset.getKey(), asset.getValue().getBytes());
        }
        return Collections.unmodifiableMap(result);
    }

    public SourceResource getResource(String type, String id) {
        return resources.get(type + "|" + Ids.parse(id));
    }

    public List<SourceResource> resources(String type) {
        List<SourceResource> result = resourcesByType.get(type);
        return result == null ? Collections.<SourceResource>emptyList() : result;
    }
}
