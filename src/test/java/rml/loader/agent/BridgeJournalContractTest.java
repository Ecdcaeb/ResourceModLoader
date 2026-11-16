package rml.loader.agent;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Journal contract: retries, ownership, queue cancellation and stop races require no game stubs. */
public final class BridgeJournalContractTest {
    private static final List<Path> roots = new ArrayList<>();
    private static final List<BridgeJournal> journals = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        try {
            duplicatesAreBoundToPayloadAndOwner();
            cancellationWinsBeforeExecution();
            runningResultsSurviveCancellation();
            shutdownCancelsQueuedWork();
            cancellationRacesExecution();
            completedRequestsOutliveTheMemoryCache();
            interruptedReceiptsNeverExecuteAgain();
            failedPersistenceRetainsTheIntent();
            System.out.println("BridgeJournalContractTest passed");
        } finally {
            for (BridgeJournal journal : journals) journal.close(payload("closed"));
            for (Path root : roots) {
                try (Stream<Path> paths = Files.walk(root)) {
                    for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void duplicatesAreBoundToPayloadAndOwner() throws Exception {
        BridgeJournal journal = journal(2);
        JsonObject payload = payload("apply");
        BridgeJournal.Entry entry = journal.submit("a", "console", "session", payload);
        check(entry == journal.submit("a", "console", "session", payload("apply")), "Identical delivery shares one entry");
        check(entry.dispatch() && !entry.dispatch(), "Duplicate request is never dispatched twice");
        rejects(() -> journal.submit("a", "console", "session", payload("deactivate")), "request_id_conflict");
        rejects(() -> journal.submit("a", "another", "different", payload), "request_id_conflict");
        rejects(() -> journal.submit("b", "another", "session", payload), "session_owner_mismatch");
        rejects(() -> journal.find("a", "another", "session"), "request_owner_mismatch");
        entry.begin();
        JsonObject response = payload("committed");
        entry.complete(response);
        check(journal.submit("a", "console", "session", payload).response.join() == response, "Finished response is retained exactly");
    }

    private static void cancellationWinsBeforeExecution() throws Exception {
        BridgeJournal.Entry entry = journal(1).submit("a", "console", "session", payload("command"));
        entry.dispatch();
        JsonObject cancelled = payload("cancelled");
        check("cancelled".equals(entry.cancel(cancelled)), "Queued cancellation is explicit");
        check(!entry.begin(), "Previously scheduled callback cannot execute after cancellation");
        entry.complete(payload("late preparation"));
        check(entry.response.join() == cancelled, "Late preparation cannot overwrite cancellation");
    }

    private static void runningResultsSurviveCancellation() throws Exception {
        BridgeJournal.Entry entry = journal(1).submit("a", "console", "session", payload("apply"));
        check(entry.begin(), "Execution begins once");
        check("running".equals(entry.cancel(payload("cancelled"))), "Already running work is not reported cancelled");
        check(!entry.response.isDone(), "Running cancellation does not invent a result");
        JsonObject committed = payload("committed");
        entry.complete(committed);
        check("completed".equals(entry.cancel(payload("cancelled"))), "Completed mutations stay committed");
        check(entry.response.join() == committed, "Committed result remains available");
    }

    private static void shutdownCancelsQueuedWork() throws Exception {
        BridgeJournal journal = journal(2);
        BridgeJournal.Entry queued = journal.submit("a", "console", "session", payload("check"));
        BridgeJournal.Entry running = journal.submit("b", "console", "session", payload("apply"));
        running.begin();
        journal.close(payload("closed"));
        check(!queued.begin(), "Stop prevents queued work from executing");
        queued.response.join();
        check("running".equals(running.status()), "Stop does not claim to undo running work");
        rejects(() -> journal.submit("c", "console", "session", payload("inspect")), "bridge_closed");
        running.complete(payload("committed"));
        running.response.join();
    }

    private static void cancellationRacesExecution() throws Exception {
        BridgeJournal journal = journal(2);
        for (int i = 0; i < 100; i++) {
            BridgeJournal.Entry entry = journal.submit("race-" + i, "console", "session", payload("command"));
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger executed = new AtomicInteger();
            Thread callback = new Thread(() -> {
                try { start.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
                if (entry.begin()) { executed.incrementAndGet(); entry.complete(payload("committed")); }
            });
            callback.start();
            start.countDown();
            String status = entry.cancel(payload("cancelled"));
            callback.join();
            if ("cancelled".equals(status)) check(executed.get() == 0, "Cancelled callback never executes");
            else check(executed.get() == 1, "Running callback executes exactly once");
            entry.response.join();
        }
    }

    private static void completedRequestsOutliveTheMemoryCache() throws Exception {
        Path root = root();
        BridgeJournal journal = journal(256, root);
        AtomicInteger actions = new AtomicInteger();
        for (int i = 0; i < 600; i++) {
            BridgeJournal.Entry entry = journal.submit("request-" + i, "console", "session", payload("command-" + i));
            if (entry.dispatch() && entry.begin()) {
                actions.incrementAndGet();
                entry.complete(payload("committed-" + i));
            }
            entry.response.join();
        }
        BridgeJournal.Entry replay = journal.submit("request-0", "console", "session", payload("command-0"));
        if (replay.dispatch() && replay.begin()) actions.incrementAndGet();
        check(actions.get() == 600, "More than 256 completed actions continue; an evicted request never executes twice");
        check(replay.response.join().equals(payload("committed-0")), "Evicted response resolves from the durable receipt");
        rejects(() -> journal.submit("request-1", "console", "session", payload("changed")), "request_id_conflict");
        rejects(() -> journal.find("request-2", "another", "session"), "request_owner_mismatch");
        journal.close(payload("closed"));
        BridgeJournal reopened = journal(2, root);
        BridgeJournal.Entry restored = reopened.submit("request-0", "console", "session", payload("command-0"));
        check(!restored.dispatch() && restored.response.join().equals(payload("committed-0")), "Completed receipts survive bridge restart");
        rejects(() -> reopened.submit("new-request", "another", "session", payload("inspect")), "session_owner_mismatch");
    }

    private static void interruptedReceiptsNeverExecuteAgain() throws Exception {
        Path root = root();
        BridgeJournal original = journal(2, root);
        BridgeJournal.Entry running = original.submit("uncertain", "console", "session", payload("apply"));
        running.dispatch();
        running.begin();
        original.close(payload("closed"));
        BridgeJournal reopened = journal(2, root);
        BridgeJournal.Entry recovered = reopened.submit("uncertain", "console", "session", payload("apply"));
        check(!recovered.dispatch() && !recovered.begin(), "An intent surviving restart is never executed again");
        check("unknown".equals(recovered.status()), "Interrupted receipt reports an unknown outcome");
        check("outcome_unknown".equals(recovered.response.join().getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString()), "Recovery explicitly requires runtime inspection");
        rejects(() -> reopened.submit("uncertain", "console", "session", payload("deactivate")), "request_id_conflict");
        running.complete(payload("committed"));
        running.response.join();
    }

    private static void failedPersistenceRetainsTheIntent() throws Exception {
        Path parent = root();
        Path storage = parent.resolve("journal");
        BridgeJournal journal = journal(1, storage);
        BridgeJournal.Entry entry = journal.submit("uncertain", "console", "session", payload("apply"));
        entry.begin();
        Path unavailable = parent.resolve("unavailable");
        Files.move(storage, unavailable, StandardCopyOption.ATOMIC_MOVE);
        entry.complete(payload("committed"));
        check(!entry.response.join().get("result_persisted").getAsBoolean(), "A failed result write is explicitly reported");
        Files.move(unavailable, storage, StandardCopyOption.ATOMIC_MOVE);
        BridgeJournal.Entry another = journal.submit("another", "console", "session", payload("inspect"));
        another.begin();
        another.complete(payload("inspected"));
        another.response.join();
        BridgeJournal.Entry replay = journal.submit("uncertain", "console", "session", payload("apply"));
        check(!replay.dispatch() && "unknown".equals(replay.status()), "The durable intent prevents replay even after a result write failure and cache eviction");
    }

    private static Path root() throws IOException {
        Path root = Files.createTempDirectory("rml-journal-contract-");
        roots.add(root);
        return root;
    }

    private static BridgeJournal journal(int capacity) throws IOException { return journal(capacity, root()); }

    private static BridgeJournal journal(int capacity, Path root) throws IOException {
        BridgeJournal journal = new BridgeJournal(capacity, root);
        journals.add(journal);
        return journal;
    }

    private static JsonObject payload(String value) {
        JsonObject object = new JsonObject();
        object.addProperty("operation", value);
        return object;
    }

    private static void rejects(Checked operation, String code) throws Exception {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) {
            check(code.equals(expected.getMessage()), "Expected " + code + ", received " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private interface Checked { void run() throws Exception; }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
