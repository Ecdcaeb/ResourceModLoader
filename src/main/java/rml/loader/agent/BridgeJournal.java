package rml.loader.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Durable receipts prevent replay; only live operations and a bounded response cache occupy memory. */
final class BridgeJournal {
    private static final int MAX_LIVE = 64;
    private final int capacity;
    private final Path root;
    private final Object lifecycle = new Object();
    private final ReentrantLock disk = new ReentrantLock();
    private final Map<String, Entry> live = new LinkedHashMap<>();
    private final Map<String, Entry> completed = new LinkedHashMap<>(16, 0.75F, true);
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_LIVE), task -> {
                Thread thread = new Thread(task, "rml-bridge-journal");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private boolean closed;

    /** Construct and call submit/find only from an HTTP/IO worker. Receipt storage outlives bridge restarts. */
    BridgeJournal(int capacity, Path root) throws IOException {
        if (capacity < 1) throw new IllegalArgumentException("Journal cache must be positive");
        this.capacity = capacity;
        this.root = root.toAbsolutePath().normalize();
        BridgeArchives.requireSafe(this.root);
        Files.createDirectories(this.root.resolve("owners"));
        BridgeArchives.requireSafe(this.root.resolve("owners"));
    }

    Entry submit(String id, String principal, String session, JsonObject payload) throws IOException {
        disk.lock();
        try {
            synchronized (lifecycle) {
                if (closed) throw new IllegalStateException("bridge_closed");
            }
            requireOwner(session, principal);
            Entry previous = lookup(id);
            if (previous != null) {
                requirePayload(previous, principal, session, payload);
                return previous;
            }
            synchronized (lifecycle) {
                if (live.size() >= MAX_LIVE) throw new IllegalStateException("bridge_busy");
            }
            Entry entry = new Entry(id, principal, session, payload);
            // CREATE_NEW never overwrites another bridge's intent. A partial receipt fails closed on read.
            try {
                create(receipt(id), record(entry, "queued", null));
            } catch (FileAlreadyExistsException raced) {
                previous = lookup(id);
                if (previous == null) throw new IOException("Request receipt disappeared", raced);
                requirePayload(previous, principal, session, payload);
                return previous;
            }
            synchronized (lifecycle) {
                if (closed) throw new IllegalStateException("bridge_closed");
                live.put(id, entry);
            }
            return entry;
        } catch (IOException error) {
            throw new JournalException(error);
        } finally {
            disk.unlock();
        }
    }

    Entry find(String id, String principal, String session) throws IOException {
        disk.lock();
        try {
            Entry entry = lookup(id);
            if (entry == null) throw new IllegalArgumentException("request_not_found");
            if (!entry.principal.equals(principal) || !entry.session.equals(session))
                throw new IllegalArgumentException("request_owner_mismatch");
            return entry;
        } catch (IOException error) {
            throw new JournalException(error);
        } finally {
            disk.unlock();
        }
    }

    private Entry lookup(String id) throws IOException {
        synchronized (lifecycle) {
            Entry entry = live.get(id);
            if (entry == null) entry = completed.get(id);
            if (entry != null) return entry;
        }
        Path path = receipt(id);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        JsonObject saved = read(path);
        try {
            if (!id.equals(saved.get("request_id").getAsString())) throw new IllegalArgumentException("Receipt id mismatch");
            Entry entry = new Entry(id, saved.get("principal").getAsString(), saved.get("session_id").getAsString(), saved.getAsJsonObject("payload"));
            if (entry.payload == null) throw new IllegalArgumentException("Missing receipt payload");
            JsonObject response = saved.has("response") ? saved.getAsJsonObject("response") : null;
            entry.status = response == null ? "unknown" : saved.get("status").getAsString();
            entry.finished = true;
            entry.dispatched = true;
            entry.response.complete(response == null ? uncertain(id) : response);
            synchronized (lifecycle) { cache(entry); }
            return entry;
        } catch (RuntimeException malformed) {
            throw new IOException("Unreadable request receipt; inspect runtime before proceeding", malformed);
        }
    }

    private void requireOwner(String session, String principal) throws IOException {
        Path path = root.resolve("owners").resolve(name(session) + ".json");
        JsonObject owner = new JsonObject();
        owner.addProperty("principal", principal);
        try { create(path, owner); }
        catch (FileAlreadyExistsException existing) {
            JsonObject saved = read(path);
            if (!saved.has("principal") || !principal.equals(saved.get("principal").getAsString()))
                throw new IllegalArgumentException("session_owner_mismatch");
        }
    }

    private static void requirePayload(Entry entry, String principal, String session, JsonObject payload) {
        if (!entry.principal.equals(principal) || !entry.session.equals(session) || !entry.payload.equals(payload))
            throw new IllegalArgumentException("request_id_conflict");
    }

    /** Never takes the disk lock or waits for persistence; safe from the native server thread. */
    void close(JsonObject cancelled) {
        ArrayList<Entry> pending;
        synchronized (lifecycle) {
            closed = true;
            pending = new ArrayList<>(live.values());
            if (pending.isEmpty()) writer.shutdown();
        }
        for (Entry entry : pending) entry.cancel(cancelled);
    }

    private void persist(Entry entry, String status, JsonObject response) {
        try {
            writer.execute(() -> {
                JsonObject delivered = response;
                disk.lock();
                try {
                    replace(receipt(entry.id), record(entry, status, response));
                } catch (IOException | RuntimeException error) {
                    // The forced intent remains on disk. A later process returns unknown, never retries it.
                    delivered = new JsonParser().parse(response.toString()).getAsJsonObject();
                    delivered.addProperty("result_persisted", false);
                    JsonArray diagnostics = delivered.has("diagnostics") ? delivered.getAsJsonArray("diagnostics") : new JsonArray();
                    JsonObject diagnostic = new JsonObject();
                    diagnostic.addProperty("code", "journal_io_error");
                    diagnostic.addProperty("message", "Result could not be persisted; its durable intent prevents replay. Inspect runtime after reconnecting.");
                    diagnostics.add(diagnostic);
                    delivered.add("diagnostics", diagnostics);
                } finally {
                    disk.unlock();
                }
                finish(entry, delivered);
            });
        } catch (RuntimeException stopped) {
            finish(entry, uncertain(entry.id));
        }
    }

    private void finish(Entry entry, JsonObject result) {
        synchronized (lifecycle) {
            live.remove(entry.id);
            cache(entry);
            if (closed && live.isEmpty()) writer.shutdown();
        }
        entry.response.complete(result);
    }

    private void cache(Entry entry) {
        completed.put(entry.id, entry);
        while (completed.size() > capacity) completed.remove(completed.keySet().iterator().next());
    }

    private Path receipt(String id) throws IOException {
        Path path = root.resolve(name(id) + ".json");
        BridgeArchives.requireSafe(path);
        return path;
    }

    private static String name(String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid receipt identifier");
        return id;
    }

    private static JsonObject record(Entry entry, String status, JsonObject response) {
        JsonObject record = new JsonObject();
        record.addProperty("request_id", entry.id);
        record.addProperty("principal", entry.principal);
        record.addProperty("session_id", entry.session);
        record.add("payload", entry.payload);
        record.addProperty("status", status);
        if (response != null) record.add("response", response);
        return record;
    }

    private static JsonObject read(Path path) throws IOException {
        BridgeArchives.requireSafe(path);
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return new JsonParser().parse(new String(BridgeArchives.bounded(input, 512 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IOException("Unreadable journal receipt; request will not execute", malformed);
        }
    }

    private static void create(Path path, JsonObject value) throws IOException {
        BridgeArchives.requireSafe(path);
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void replace(Path path, JsonObject value) throws IOException {
        BridgeArchives.requireSafe(path);
        Path temporary = Files.createTempFile(path.getParent(), ".receipt-", ".tmp");
        try {
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            BridgeArchives.requireSafe(path);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject uncertain(String id) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        JsonObject result = new JsonObject();
        result.addProperty("request_id", id);
        result.addProperty("status", "unknown");
        response.add("result", result);
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", "outcome_unknown");
        diagnostic.addProperty("message", "A durable request intent has no durable result. It will not execute again; inspect runtime before deciding what to do next.");
        JsonArray diagnostics = new JsonArray();
        diagnostics.add(diagnostic);
        response.add("diagnostics", diagnostics);
        JsonObject state = new JsonObject();
        state.add("session", JsonNull.INSTANCE);
        state.add("revision", JsonNull.INSTANCE);
        response.add("state", state);
        return response;
    }

    static final class JournalException extends IOException {
        JournalException(IOException cause) {
            super("Request journal unavailable; existing receipts will not be executed again. Inspect runtime before deciding what to do next.", cause);
        }
    }

    final class Entry {
        final String id;
        final String principal;
        final String session;
        final JsonObject payload;
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        private boolean dispatched;
        private boolean finished;
        private String status = "queued";

        Entry(String id, String principal, String session, JsonObject payload) {
            this.id = id;
            this.principal = principal;
            this.session = session;
            this.payload = payload;
        }

        synchronized boolean dispatch() {
            if (dispatched || !"queued".equals(status)) return false;
            dispatched = true;
            return true;
        }

        synchronized boolean begin() {
            if (!"queued".equals(status)) return false;
            status = "running";
            return true;
        }

        synchronized String cancel(JsonObject result) {
            if ("queued".equals(status)) {
                status = "cancelled";
                finished = true;
                persist(this, status, result);
            }
            return status;
        }

        synchronized void complete(JsonObject result) {
            if (finished) return;
            finished = true;
            status = "completed";
            persist(this, status, result);
        }

        synchronized String status() { return status; }
    }
}
