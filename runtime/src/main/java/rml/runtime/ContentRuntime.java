package rml.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Dependency-ordered, transactional activation of reversible content resources.
 * Preparation may run on another thread; mutations use the injected thread guard.
 * A cleanup failure makes further activation unavailable. If old-resource cleanup
 * fails after a commit, the new generation remains committed and active.
 */
public final class ContentRuntime implements AutoCloseable {
    private final Runnable checkThread;
    private final Map<String, LoadedPack> active = new TreeMap<String, LoadedPack>();
    private long revision;
    private boolean closed;
    private boolean mutating;
    private ContentException cleanupFailure;

    public ContentRuntime(Runnable checkThread) {
        if (checkThread == null) {
            throw new NullPointerException("checkThread");
        }
        this.checkThread = checkThread;
    }

    /** Validates the complete effective graph before calling any unit's prepare. */
    public Candidate prepare(Collection<PackDefinition> definitions) {
        final long expectedRevision;
        final Map<String, PackDefinition> previous = new TreeMap<String, PackDefinition>();
        synchronized (this) {
            requireOpen();
            requireHealthy();
            expectedRevision = revision;
            for (LoadedPack pack : active.values()) {
                previous.put(pack.definition.getId(), pack.definition);
            }
        }
        if (definitions == null) {
            throw PackDefinition.invalid(null, null, "Pack definitions must not be null");
        }
        Map<String, PackDefinition> replacements = new TreeMap<String, PackDefinition>();
        for (PackDefinition definition : definitions) {
            if (definition == null) {
                throw PackDefinition.invalid(null, null, "Pack definition must not be null");
            }
            if (replacements.put(definition.getId(), definition) != null) {
                throw PackDefinition.invalid(definition.getId(), null, "Duplicate pack ID");
            }
        }
        for (PackDefinition dependent : previous.values()) {
            if (!replacements.containsKey(dependent.getId())) {
                for (String dependency : dependent.getDependencies()) {
                    if (replacements.containsKey(dependency)) {
                        throw new ContentException("invalid_dependency", dependent.getId(), null,
                                "Replacing dependency " + dependency + " requires replacing dependent "
                                        + dependent.getId());
                    }
                }
            }
        }
        Map<String, PackDefinition> effective = new TreeMap<String, PackDefinition>(previous);
        effective.putAll(replacements);
        List<String> packOrder = order(packGraph(effective), null);
        List<PreparedPack> plans = new ArrayList<PreparedPack>(replacements.size());
        for (String packId : packOrder) {
            PackDefinition definition = replacements.get(packId);
            if (definition == null) {
                continue;
            }
            Map<String, ContentUnit> units = new TreeMap<String, ContentUnit>();
            Map<String, List<String>> graph = new TreeMap<String, List<String>>();
            for (ContentUnit unit : definition.getUnits()) {
                String unitId = PackDefinition.identifier(unit.getId(), packId, null);
                if (units.put(unitId, unit) != null) {
                    throw PackDefinition.invalid(packId, unitId, "Duplicate content unit ID");
                }
                Collection<String> dependencies = unit.getDependencies();
                if (dependencies == null) {
                    throw PackDefinition.invalid(packId, unitId, "Unit dependencies must not be null");
                }
                TreeSet<String> dependencyIds = new TreeSet<String>();
                for (String dependency : dependencies) {
                    PackDefinition.identifier(dependency, packId, unitId);
                    if (!dependencyIds.add(dependency)) {
                        throw new ContentException("invalid_dependency", packId, unitId,
                                "Duplicate unit dependency: " + dependency);
                    }
                }
                graph.put(unitId, new ArrayList<String>(dependencyIds));
            }
            List<String> unitOrder = order(graph, packId);
            plans.add(new PreparedPack(definition, unitOrder, units));
        }
        for (PreparedPack plan : plans) {
            for (String unitId : plan.unitIds) {
                try {
                    PreparedUnit prepared = plan.units.get(unitId).prepare();
                    if (prepared == null) {
                        throw new NullPointerException("ContentUnit.prepare returned null");
                    }
                    plan.prepared.add(prepared);
                } catch (Throwable cause) {
                    throw new ContentException("prepare_failed", plan.definition.getId(), unitId,
                            "Failed to prepare content unit " + unitId, cause);
                }
            }
        }
        return new Candidate(this, expectedRevision, plans);
    }

    /**
     * Stages every resource inactive, then commits all scopes before old cleanup.
     * An exception with code cleanup_failed after commit leaves the new snapshot in effect.
     */
    public synchronized void apply(Candidate candidate) {
        checkThread.run();
        requireMutationAllowed();
        requireHealthy();
        if (candidate == null || candidate.owner != this || candidate.used
                || candidate.expectedRevision != revision) {
            throw new ContentException("stale_candidate", null, null,
                    "Candidate is foreign, consumed, or prepared against another revision");
        }
        candidate.used = true;
        mutating = true;
        List<LoadedPack> staged = new ArrayList<LoadedPack>(candidate.packs.size());
        try {
            for (PreparedPack plan : candidate.packs) {
                staged.add(new LoadedPack(plan.definition, plan.unitIds, new ResourceScope(), revision + 1));
            }
            String activatingPack = null;
            String activatingUnit = null;
            try {
                for (int p = 0; p < candidate.packs.size(); p++) {
                    PreparedPack plan = candidate.packs.get(p);
                    LoadedPack pack = staged.get(p);
                    activatingPack = plan.definition.getId();
                    for (int u = 0; u < plan.prepared.size(); u++) {
                        activatingUnit = plan.unitIds.get(u);
                        plan.prepared.get(u).activate(pack.scope);
                    }
                }
                for (LoadedPack pack : staged) {
                    if (pack.scope.isClosed()) {
                        activatingPack = pack.definition.getId();
                        activatingUnit = null;
                        throw new IllegalStateException("Activation closed its runtime-owned scope");
                    }
                }
            } catch (Throwable cause) {
                ContentException failure = new ContentException("activation_failed", activatingPack,
                        activatingUnit, "Failed to activate candidate", cause);
                ContentException cleanup = clean(staged);
                if (cleanup != null) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
            List<LoadedPack> replaced = new ArrayList<LoadedPack>();
            for (String packId : activeOrder()) {
                if (candidate.packIds.contains(packId)) {
                    LoadedPack old = active.get(packId);
                    old.scope.setActive(false);
                    replaced.add(old);
                }
            }
            for (LoadedPack pack : staged) {
                pack.scope.setActive(true);
                active.put(pack.definition.getId(), pack);
            }
            if (!staged.isEmpty()) {
                revision++;
            }
            ContentException cleanup = clean(replaced);
            if (cleanup != null) {
                throw cleanup;
            }
        } finally {
            mutating = false;
        }
    }

    public synchronized void deactivate(Collection<String> packIds) {
        checkThread.run();
        requireMutationAllowed();
        if (packIds == null) {
            throw PackDefinition.invalid(null, null, "Pack IDs must not be null");
        }
        TreeSet<String> removing = new TreeSet<String>();
        for (String id : packIds) {
            PackDefinition.identifier(id, null, null);
            if (!removing.add(id)) {
                throw PackDefinition.invalid(id, null, "Duplicate pack ID");
            }
            if (!active.containsKey(id)) {
                throw new ContentException("missing_dependency", id, null, "Pack is not active: " + id);
            }
        }
        for (LoadedPack pack : active.values()) {
            if (!removing.contains(pack.definition.getId())) {
                for (String dependency : pack.definition.getDependencies()) {
                    if (removing.contains(dependency)) {
                        throw new ContentException("invalid_dependency", pack.definition.getId(), null,
                                "Cannot deactivate dependency " + dependency + " of active pack "
                                        + pack.definition.getId());
                    }
                }
            }
        }
        List<LoadedPack> removed = new ArrayList<LoadedPack>(removing.size());
        for (String id : activeOrder()) {
            if (removing.contains(id)) {
                LoadedPack pack = active.remove(id);
                pack.scope.setActive(false);
                removed.add(pack);
            }
        }
        if (!removed.isEmpty()) {
            revision++;
        }
        mutating = true;
        try {
            ContentException cleanup = clean(removed);
            if (cleanup != null) {
                throw cleanup;
            }
        } finally {
            mutating = false;
        }
    }

    public synchronized List<PackSnapshot> snapshot() {
        List<PackSnapshot> snapshots = new ArrayList<PackSnapshot>(active.size());
        for (LoadedPack pack : active.values()) {
            snapshots.add(new PackSnapshot(pack));
        }
        return Collections.unmodifiableList(snapshots);
    }

    /** Monotonic commit revision, including removals when no packs remain active. */
    public synchronized long getRevision() { return revision; }

    @Override
    public synchronized void close() {
        checkThread.run();
        if (closed) {
            return;
        }
        requireMutationAllowed();
        List<LoadedPack> closing = new ArrayList<LoadedPack>(active.size());
        for (String id : activeOrder()) {
            LoadedPack pack = active.get(id);
            pack.scope.setActive(false);
            closing.add(pack);
        }
        active.clear();
        closed = true;
        revision++;
        mutating = true;
        try {
            ContentException cleanup = clean(closing);
            if (cleanup != null) {
                throw cleanup;
            }
        } finally {
            mutating = false;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new ContentException("runtime_closed", null, null, "Content runtime is closed");
        }
    }

    private void requireMutationAllowed() {
        requireOpen();
        if (mutating) {
            throw new ContentException("invalid_state", null, null, "Reentrant runtime mutation");
        }
    }

    private void requireHealthy() {
        if (cleanupFailure != null) {
            throw new ContentException("cleanup_failed", null, null,
                    "Runtime is unhealthy after resource cleanup failed", cleanupFailure);
        }
    }

    private ContentException clean(List<LoadedPack> packs) {
        ContentException failure = null;
        for (int i = packs.size() - 1; i >= 0; i--) {
            LoadedPack pack = packs.get(i);
            try {
                pack.scope.close();
            } catch (Throwable cause) {
                if (failure == null) {
                    failure = new ContentException("cleanup_failed", pack.definition.getId(), null,
                            "Failed to clean resources for pack " + pack.definition.getId(), cause);
                } else {
                    failure.addSuppressed(cause);
                }
            }
        }
        if (failure != null) {
            cleanupFailure = failure;
        }
        return failure;
    }

    private List<String> activeOrder() {
        Map<String, PackDefinition> definitions = new TreeMap<String, PackDefinition>();
        for (LoadedPack pack : active.values()) {
            definitions.put(pack.definition.getId(), pack.definition);
        }
        return order(packGraph(definitions), null);
    }


    private static Map<String, List<String>> packGraph(Map<String, PackDefinition> definitions) {
        Map<String, List<String>> graph = new TreeMap<String, List<String>>();
        for (PackDefinition definition : definitions.values()) {
            graph.put(definition.getId(), definition.getDependencies());
        }
        return graph;
    }

    /** Kahn ordering with lexical tie-breaking avoids depending on input collection order. */
    private static List<String> order(Map<String, List<String>> graph, String packId) {
        Map<String, Integer> remaining = new TreeMap<String, Integer>();
        Map<String, List<String>> dependents = new HashMap<String, List<String>>();
        PriorityQueue<String> ready = new PriorityQueue<String>();
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            String id = entry.getKey();
            remaining.put(id, entry.getValue().size());
            for (String dependency : entry.getValue()) {
                if (!graph.containsKey(dependency)) {
                    throw new ContentException("missing_dependency", packId == null ? id : packId,
                            packId == null ? null : id, "Missing dependency " + dependency + " of " + id);
                }
                List<String> users = dependents.get(dependency);
                if (users == null) {
                    users = new ArrayList<String>();
                    dependents.put(dependency, users);
                }
                users.add(id);
            }
            if (entry.getValue().isEmpty()) {
                ready.add(id);
            }
        }
        List<String> result = new ArrayList<String>(graph.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            result.add(id);
            List<String> users = dependents.get(id);
            if (users != null) {
                for (String user : users) {
                    int count = remaining.get(user) - 1;
                    remaining.put(user, count);
                    if (count == 0) {
                        ready.add(user);
                    }
                }
            }
        }
        if (result.size() != graph.size()) {
            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                if (entry.getValue() > 0) {
                    throw new ContentException("cyclic_dependency", packId == null ? entry.getKey() : packId,
                            packId == null ? null : entry.getKey(), "Dependency cycle involving " + entry.getKey());
                }
            }
        }
        return result;
    }

    /** An opaque, single-use preparation result bound to its creating runtime and revision. */
    public static final class Candidate {
        private final ContentRuntime owner;
        private final long expectedRevision;
        private final List<PreparedPack> packs;
        private final TreeSet<String> packIds = new TreeSet<String>();
        private boolean used;

        private Candidate(ContentRuntime owner, long expectedRevision, List<PreparedPack> packs) {
            this.owner = owner;
            this.expectedRevision = expectedRevision;
            this.packs = Collections.unmodifiableList(packs);
            for (PreparedPack pack : packs) {
                packIds.add(pack.definition.getId());
            }
        }
    }

    /** Immutable metadata detached from scopes and future lifecycle changes. */
    public static final class PackSnapshot {
        private final String id;
        private final String version;
        private final long generation;
        private final List<String> unitIds;
        private final int resourceCount;

        private PackSnapshot(LoadedPack pack) {
            id = pack.definition.getId();
            version = pack.definition.getVersion();
            generation = pack.generation;
            unitIds = pack.unitIds;
            resourceCount = pack.scope.size();
        }

        public String getId() { return id; }
        public String getVersion() { return version; }
        public long getGeneration() { return generation; }
        public List<String> getUnitIds() { return unitIds; }
        public int getResourceCount() { return resourceCount; }
    }

    private static final class PreparedPack {
        private final PackDefinition definition;
        private final List<String> unitIds;
        private final Map<String, ContentUnit> units;
        private final List<PreparedUnit> prepared;

        private PreparedPack(PackDefinition definition, List<String> unitIds, Map<String, ContentUnit> units) {
            this.definition = definition;
            this.unitIds = Collections.unmodifiableList(unitIds);
            this.units = units;
            this.prepared = new ArrayList<PreparedUnit>(unitIds.size());
        }
    }

    private static final class LoadedPack {
        private final PackDefinition definition;
        private final List<String> unitIds;
        private final ResourceScope scope;
        private final long generation;

        private LoadedPack(PackDefinition definition, List<String> unitIds, ResourceScope scope, long generation) {
            this.definition = definition;
            this.unitIds = unitIds;
            this.scope = scope;
            this.generation = generation;
        }
    }
}
