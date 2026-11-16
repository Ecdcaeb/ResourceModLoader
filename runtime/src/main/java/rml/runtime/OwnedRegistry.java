package rml.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reversible contributions to a mutable map, confined to the caller's game thread.
 * Values are compared by identity, so an equal external replacement is preserved.
 * Closing a contribution restores only a still-open older contribution, and only
 * while the map still contains the closing contribution's value. Null keys and
 * values are supported when the backing map supports them.
 */
public final class OwnedRegistry<K, V> {
    private Map<K, V> target;
    private final Map<K, Registration> contributions = new LinkedHashMap<K, Registration>();

    public OwnedRegistry(Map<K, V> target) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        this.target = target;
    }

    /** Publishes a contribution, rejecting any entry not currently owned by this registry. */
    public AutoCloseable register(K key, V value) {
        Registration previous = contributions.get(key);
        if (target.containsKey(key) && (previous == null || !previous.published
                || target.get(key) != previous.value)) {
            throw new IllegalStateException("Cannot overwrite an unmanaged registry entry: " + key);
        }
        Registration registration = new Registration(key, value, previous);
        target.put(key, value);
        if (previous != null) {
            previous.newer = registration;
        }
        contributions.put(key, registration);
        return registration;
    }

    /** Reports whether the current map entry is the latest open contribution. */
    public boolean owns(K key) {
        Registration current = contributions.get(key);
        return current != null && current.published && target.containsKey(key)
                && target.get(key) == current.value;
    }

    /**
     * Moves open contributions to a replacement mutable map. All managed keys are
     * checked before either map changes. Missing entries and the identical current
     * owned value are accepted; all other entries reject the entire rebind.
     * Still-owned entries are removed from the previous map; foreign entries remain.
     */
    public void rebind(Map<K, V> replacement) {
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        for (Registration current : contributions.values()) {
            if (replacement.containsKey(current.key) && replacement.get(current.key) != current.value) {
                throw new IllegalStateException("Cannot overwrite an unmanaged registry entry: " + current.key);
            }
        }
        for (Registration current : contributions.values()) {
            replacement.put(current.key, current.value);
        }
        if (target != replacement) {
            for (Registration current : contributions.values()) {
                if (current.published && target.containsKey(current.key)
                        && target.get(current.key) == current.value) {
                    target.remove(current.key);
                }
            }
            target = replacement;
        }
        for (Registration current : contributions.values()) {
            current.published = true;
        }
    }

    private final class Registration implements AutoCloseable {
        private final K key;
        private final V value;
        private Registration older;
        private Registration newer;
        private boolean closed;
        private boolean published = true;

        private Registration(K key, V value, Registration older) {
            this.key = key;
            this.value = value;
            this.older = older;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (newer == null) {
                boolean owned = published && target.containsKey(key) && target.get(key) == value;
                if (owned) {
                    if (older == null) {
                        target.remove(key);
                    } else {
                        target.put(key, older.value);
                    }
                }
                if (older != null) {
                    older.published = owned;
                }
                if (older == null) {
                    contributions.remove(key);
                } else {
                    contributions.put(key, older);
                }
            } else {
                newer.older = older;
            }
            if (older != null) {
                older.newer = newer;
            }
            older = null;
            newer = null;
            closed = true;
        }
    }
}
