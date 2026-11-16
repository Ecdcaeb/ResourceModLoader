package rml.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable pack metadata; content units themselves must remain stable during preparation. */
public final class PackDefinition {
    private final String id;
    private final String version;
    private final List<String> dependencies;
    private final List<ContentUnit> units;

    public PackDefinition(String id, String version, Collection<String> dependencies,
                          Collection<? extends ContentUnit> units) {
        this.id = identifier(id, null, null);
        if (version == null) {
            throw invalid(id, null, "Pack version must not be null");
        }
        this.version = version;
        if (dependencies == null || units == null) {
            throw invalid(id, null, "Pack collections must not be null");
        }
        List<String> dependencyCopy = new ArrayList<String>(dependencies.size());
        Set<String> dependencyIds = new HashSet<String>();
        for (String dependency : dependencies) {
            identifier(dependency, id, null);
            if (id.equals(dependency) || !dependencyIds.add(dependency)) {
                throw new ContentException("invalid_dependency", id, null,
                        "Duplicate or self pack dependency: " + dependency);
            }
            dependencyCopy.add(dependency);
        }
        this.dependencies = Collections.unmodifiableList(dependencyCopy);
        List<ContentUnit> unitCopy = new ArrayList<ContentUnit>(units.size());
        Set<String> unitIds = new HashSet<String>();
        for (ContentUnit unit : units) {
            if (unit == null) {
                throw invalid(id, null, "Content unit must not be null");
            }
            String unitId = identifier(unit.getId(), id, null);
            if (!unitIds.add(unitId)) {
                throw invalid(id, unitId, "Duplicate content unit: " + unitId);
            }
            unitCopy.add(unit);
        }
        this.units = Collections.unmodifiableList(unitCopy);
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<ContentUnit> getUnits() {
        return units;
    }

    static String identifier(String value, String packId, String unitId) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(packId, unitId, "Identifier must not be null or empty");
        }
        return value;
    }

    static ContentException invalid(String packId, String unitId, String message) {
        return new ContentException("invalid_definition", packId, unitId, message);
    }
}
