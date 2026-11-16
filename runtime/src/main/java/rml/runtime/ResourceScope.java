package rml.runtime;

import java.util.ArrayList;
import java.util.List;

/** Owns resources in registration order and releases them in reverse order. */
public final class ResourceScope implements AutoCloseable {
    private final List<AutoCloseable> resources = new ArrayList<AutoCloseable>();
    private volatile boolean active;
    private boolean closed;

    public synchronized <T extends AutoCloseable> T own(T resource) {
        if (closed) {
            throw new IllegalStateException("Resource scope is closed");
        }
        if (resource == null) {
            throw new NullPointerException("resource");
        }
        resources.add(resource);
        return resource;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized int size() {
        return closed ? 0 : resources.size();
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized void setActive(boolean active) {
        if (active && closed) {
            throw new IllegalStateException("Cannot activate a closed resource scope");
        }
        this.active = active;
    }

    @Override
    public void close() {
        List<AutoCloseable> closing;
        synchronized (this) {
            if (closed) {
                return;
            }
            active = false;
            closed = true;
            closing = resources;
        }
        ContentException failure = null;
        for (int i = closing.size() - 1; i >= 0; i--) {
            try {
                closing.get(i).close();
            } catch (Throwable cause) {
                if (failure == null) {
                    failure = new ContentException("cleanup_failed", null, null,
                            "Failed to close owned resources", cause);
                } else {
                    failure.addSuppressed(cause);
                }
            }
        }
        synchronized (this) {
            resources.clear();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
