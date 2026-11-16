package rml.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Run with java rml.runtime.RuntimeContractTest; no test framework is required. */
public final class RuntimeContractTest {
    private static final Runnable ANY_THREAD = () -> { };

    public static void main(String[] args) throws Exception {
        definitionsAreDefensive();
        scopesCleanInReverseAndAggregateFailures();
        dependenciesOrderAndProtectLifecycle();
        entireGraphValidatesBeforePreparation();
        failuresPreserveActiveGeneration();
        candidatesAreBoundToRuntimeAndRevision();
        cleanupFailureAfterCommitIsExplicit();
        rollbackCleanupFailureDisablesActivation();
        closeCleansEveryPackAfterFailure();
        snapshotsAreDetachedAndImmutable();
        injectedThreadGuardAndConcurrentPreparation();
        repeatedReplacementRestoresBaseline();
        registryHandlesGenerationOverlap();
        registryPreservesUnmanagedEntries();
        registryRollsBackFailedActivation();
        registryRebindsWithoutClobberingEntries();
        System.out.println("RuntimeContractTest passed");
    }

    private static void registryHandlesGenerationOverlap() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        OwnedRegistry<String, Object> registry = new OwnedRegistry<String, Object>(map);
        Object oldest = new Object();
        Object middle = new Object();
        Object newest = new Object();
        AutoCloseable first = registry.register("key", oldest);
        AutoCloseable second = registry.register("key", middle);
        AutoCloseable third = registry.register("key", newest);
        check(map.get("key") == newest && registry.owns("key"), "Latest contribution owns the entry");
        second.close();
        check(map.get("key") == newest, "Closing a middle generation preserves the latest entry");
        third.close();
        check(map.get("key") == oldest, "Closing latest restores only the remaining open generation");
        second.close();
        first.close();
        third.close();
        check(!map.containsKey("key") && !registry.owns("key"), "Every handle is idempotent");

        first = registry.register("key", oldest);
        second = registry.register("key", middle);
        first.close();
        check(map.get("key") == middle, "Old generation cleanup cannot remove its replacement");
        second.close();
        check(!map.containsKey("key"), "Replacement cleanup never resurrects an already-closed generation");

        AutoCloseable nullValue = registry.register(null, null);
        check(map.containsKey(null) && registry.owns(null), "Null contributions use presence as well as identity");
        nullValue.close();
        check(!map.containsKey(null), "Null-valued contributions are removable");
    }

    private static void registryPreservesUnmanagedEntries() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        OwnedRegistry<String, Object> registry = new OwnedRegistry<String, Object>(map);
        Object external = new Object();
        map.put("foreign", external);
        expect(IllegalStateException.class, () -> registry.register("foreign", new Object()));
        check(map.get("foreign") == external, "Registration preserves unmanaged entries");
        map.put("null", null);
        expect(IllegalStateException.class, () -> registry.register("null", new Object()));
        check(map.containsKey("null"), "An unmanaged null entry is still occupied");

        String owned = new String("same value");
        String foreign = new String("same value");
        AutoCloseable first = registry.register("key", owned);
        map.put("key", foreign);
        check(!registry.owns("key"), "Equal external replacements do not count as owned");
        expect(IllegalStateException.class, () -> registry.register("key", new Object()));
        first.close();
        check(map.get("key") == foreign, "Cleanup preserves an equal but foreign replacement");

        AutoCloseable older = registry.register("other", new Object());
        AutoCloseable newer = registry.register("other", new Object());
        map.put("other", external);
        newer.close();
        older.close();
        check(map.get("other") == external, "Closing every overlapping generation preserves foreign replacement");

        Object displacedValue = new Object();
        AutoCloseable displaced = registry.register("displaced", displacedValue);
        AutoCloseable latest = registry.register("displaced", new Object());
        map.put("displaced", displacedValue);
        latest.close();
        check(!registry.owns("displaced"), "A foreign replacement using an older value stays unmanaged");
        expect(IllegalStateException.class, () -> registry.register("displaced", new Object()));
        displaced.close();
        check(map.get("displaced") == displacedValue, "Closing an older handle preserves a detected foreign entry");
    }

    private static void registryRollsBackFailedActivation() {
        Map<String, Object> map = new HashMap<String, Object>();
        OwnedRegistry<String, Object> registry = new OwnedRegistry<String, Object>(map);
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        Object oldValue = new Object();
        Object newValue = new Object();
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "1", unit("entry", strings(),
                () -> scope -> scope.own(registry.register("key", oldValue)))))));
        code("activation_failed", () -> runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "bad",
                unit("entry", strings(), () -> scope -> {
                    scope.own(registry.register("key", newValue));
                    scope.own(registry.register("temporary", new Object()));
                    throw new Exception("failure after registry publication");
                }))))));
        check(map.get("key") == oldValue, "Failed staging restores the active generation's entry");
        check(!map.containsKey("temporary"), "Failed staging removes newly introduced entries");
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "2", unit("entry", strings(),
                () -> scope -> scope.own(registry.register("key", newValue)))))));
        check(map.get("key") == newValue, "Old generation cleanup preserves the committed replacement");
        runtime.deactivate(strings("p"));
        check(!map.containsKey("key"), "Unload cannot restore the closed old generation");
        runtime.close();
    }

    private static void registryRebindsWithoutClobberingEntries() throws Exception {
        Map<String, Object> original = new HashMap<String, Object>();
        OwnedRegistry<String, Object> registry = new OwnedRegistry<String, Object>(original);
        Object oldValue = new Object();
        Object currentValue = new Object();
        Object anotherValue = new Object();
        Object external = new Object();
        AutoCloseable older = registry.register("a", oldValue);
        AutoCloseable current = registry.register("a", currentValue);
        AutoCloseable another = registry.register("b", anotherValue);
        Map<String, Object> conflict = new HashMap<String, Object>();
        conflict.put("b", external);
        expect(IllegalStateException.class, () -> registry.rebind(conflict));
        check(!conflict.containsKey("a"), "Rebind validates every key before publishing any entry");
        check(conflict.get("b") == external, "Rejected rebind preserves target entries");
        check(original.get("a") == currentValue && original.get("b") == anotherValue,
                "Rejected rebind preserves the original map and ownership");

        original.put("b", external);
        Map<String, Object> replacement = new HashMap<String, Object>();
        replacement.put("a", currentValue);
        replacement.put("unmanaged", external);
        registry.rebind(replacement);
        check(replacement.get("a") == currentValue && replacement.get("b") == anotherValue,
                "Rebind accepts an identical owned value and publishes missing entries");
        check(!original.containsKey("a"), "Rebind detaches owned entries from the previous map");
        check(original.get("b") == external, "Rebind preserves foreign replacements in the previous map");
        check(replacement.get("unmanaged") == external, "Rebind preserves unrelated target entries");
        check(registry.owns("a") && registry.owns("b"), "Ownership follows the new map");
        older.close();
        current.close();
        check(!replacement.containsKey("a"), "Rebound handles do not resurrect closed old generations");
        replacement.clear();
        check(!registry.owns("b"), "Clearing the map removes actual ownership");
        registry.rebind(replacement);
        check(replacement.get("b") == anotherValue, "Rebinding the same cleared map republishes live contributions");
        another.close();
        check(!replacement.containsKey("b"), "Existing handles clean the rebound map");

        AutoCloseable base = registry.register("rollback", oldValue);
        AutoCloseable staged = registry.register("rollback", currentValue);
        Map<String, Object> next = new HashMap<String, Object>();
        registry.rebind(next);
        staged.close();
        check(next.get("rollback") == oldValue, "Rollback after rebind restores a still-open older contribution");
        base.close();
        check(next.isEmpty(), "Rebound registry returns to its baseline");
    }

    private static void definitionsAreDefensive() {
        List<String> dependencies = new ArrayList<String>(Collections.singletonList("base"));
        List<ContentUnit> units = new ArrayList<ContentUnit>(Collections.singletonList(emptyUnit("one")));
        PackDefinition definition = new PackDefinition("pack", "1", dependencies, units);
        dependencies.clear();
        units.clear();
        equal(Collections.singletonList("base"), definition.getDependencies(), "Dependencies copied");
        equal(1, definition.getUnits().size(), "Units copied");
        expect(UnsupportedOperationException.class, () -> definition.getDependencies().clear());
        expect(UnsupportedOperationException.class, () -> definition.getUnits().clear());
        code("invalid_definition", () -> pack("", "1", emptyUnit("u")));
        code("invalid_definition", () -> pack(null, "1", emptyUnit("u")));
        code("invalid_definition", () -> pack("p", null, emptyUnit("u")));
        code("invalid_definition", () -> pack("p", "1", emptyUnit(" ")));
        code("invalid_definition", () -> pack("p", "1", emptyUnit("u"), emptyUnit("u")));
        code("invalid_dependency", () -> new PackDefinition("p", "1", strings("p"), noUnits()));
        code("invalid_dependency", () -> new PackDefinition("p", "1", strings("b", "b"), noUnits()));
        code("invalid_definition", () -> new PackDefinition("p", "1", null, noUnits()));
        code("invalid_definition", () -> new PackDefinition("p", "1", strings(), null));
    }

    private static void scopesCleanInReverseAndAggregateFailures() {
        ResourceScope scope = new ResourceScope();
        check(!scope.isActive(), "A new scope starts inactive");
        List<Integer> closed = new ArrayList<Integer>();
        AutoCloseable first = () -> closed.add(1);
        check(scope.own(first) == first, "own returns the original resource");
        scope.own(() -> {
            closed.add(2);
            throw new Exception("second");
        });
        scope.own(() -> {
            check(!scope.isActive(), "Callbacks disabled before cleanup");
            equal(0, scope.size(), "Closing scope no longer exposes owned resources");
            closed.add(3);
            throw new Exception("third");
        });
        equal(3, scope.size(), "Every resource is owned");
        ContentException failure = code("cleanup_failed", scope::close);
        equal("third", failure.getCause().getMessage(), "First cleanup failure is the cause");
        equal(1, failure.getSuppressed().length, "Remaining cleanup failure is suppressed");
        equal(Arrays.asList(3, 2, 1), closed, "Reverse cleanup continues after failures");
        scope.close();
        equal(3, closed.size(), "Close is idempotent");
        expect(IllegalStateException.class, () -> scope.own(() -> { }));
        expect(NullPointerException.class, () -> new ResourceScope().own(null));
    }

    private static void dependenciesOrderAndProtectLifecycle() {
        Probe probe = new Probe();
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        PackDefinition base = new PackDefinition("z-base", "1", strings(), Arrays.asList(
                probe.unit("c", strings("b")), probe.unit("b", strings("a")), probe.unit("a", strings())));
        PackDefinition top = new PackDefinition("a-top", "1", strings("z-base"),
                Collections.singletonList(probe.unit("top", strings())));
        ContentRuntime.Candidate candidate = runtime.prepare(Arrays.asList(top, base));
        equal(Arrays.asList("prepare:a", "prepare:b", "prepare:c", "prepare:top"), probe.events,
                "Pack and unit preparation follows dependencies");
        equal(0, probe.resources, "Preparation owns no live registrations");
        runtime.apply(candidate);
        equal(Arrays.asList("prepare:a", "prepare:b", "prepare:c", "prepare:top",
                "activate:a", "activate:b", "activate:c", "activate:top"), probe.events,
                "Activation follows the same order");
        equal("a-top", runtime.snapshot().get(0).getId(), "Snapshots use stable lexical order");
        equal(Arrays.asList("a", "b", "c"), runtime.snapshot().get(1).getUnitIds(),
                "Snapshot units follow dependency order");
        code("invalid_dependency", () -> runtime.deactivate(strings("z-base")));
        code("invalid_dependency", () -> runtime.prepare(Collections.singletonList(base)));
        equal(4, probe.resources, "Rejected mutations preserve resources");
        probe.events.clear();
        runtime.apply(runtime.prepare(Arrays.asList(base, top)));
        equal(Arrays.asList("prepare:a", "prepare:b", "prepare:c", "prepare:top",
                "activate:a", "activate:b", "activate:c", "activate:top",
                "close:top", "close:c", "close:b", "close:a"), probe.events,
                "Replacement cleans old dependent packs and units first");
        probe.events.clear();
        runtime.deactivate(strings("z-base", "a-top"));
        equal(Arrays.asList("close:top", "close:c", "close:b", "close:a"), probe.events,
                "Batch deactivation closes reverse dependencies");
        equal(0, probe.resources, "All resources released");
        runtime.close();
    }

    private static void entireGraphValidatesBeforePreparation() {
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        AtomicInteger prepared = new AtomicInteger();
        ContentUnit valid = unit("u", strings(), () -> {
            prepared.incrementAndGet();
            return scope -> { };
        });
        PackDefinition good = pack("a-good", "1", valid);
        PackDefinition bad = pack("z-bad", "1", unit("u", strings("absent"), () -> scope -> { }));
        ContentException missingUnit = code("missing_dependency", () -> runtime.prepare(Arrays.asList(good, bad)));
        equal("z-bad", missingUnit.getPackId(), "Missing unit reports pack context");
        equal("u", missingUnit.getUnitId(), "Missing unit reports unit context");
        equal(0, prepared.get(), "Entire batch is validated before the first prepare call");
        PackDefinition missingPack = new PackDefinition("p", "1", strings("missing"), noUnits());
        code("missing_dependency", () -> runtime.prepare(Collections.singletonList(missingPack)));
        PackDefinition a = new PackDefinition("a", "1", strings("b"), noUnits());
        PackDefinition b = new PackDefinition("b", "1", strings("a"), noUnits());
        code("cyclic_dependency", () -> runtime.prepare(Arrays.asList(b, a)));
        PackDefinition unitCycle = pack("p", "1", unit("a", strings("b"), () -> scope -> { }),
                unit("b", strings("a"), () -> scope -> { }));
        code("cyclic_dependency", () -> runtime.prepare(Collections.singletonList(unitCycle)));
        code("cyclic_dependency", () -> runtime.prepare(Collections.singletonList(
                pack("p", "1", unit("a", strings("a"), () -> scope -> { })))));
        code("invalid_dependency", () -> runtime.prepare(Collections.singletonList(
                pack("p", "1", unit("a", strings("b", "b"), () -> scope -> { }), emptyUnit("b")))));
        code("invalid_definition", () -> runtime.prepare(Arrays.asList(good, good)));
        code("missing_dependency", () -> runtime.deactivate(strings("missing")));
        runtime.close();
    }

    private static void failuresPreserveActiveGeneration() {
        Probe probe = new Probe();
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "old", probe.unit("old", strings())))));
        long generation = runtime.snapshot().get(0).getGeneration();
        ResourceScope oldScope = probe.scopes.get(0);
        Exception prepareCause = new Exception("prepare failure");
        ContentException preparing = code("prepare_failed", () -> runtime.prepare(Collections.singletonList(
                pack("p", "new", unit("broken", strings(), () -> { throw prepareCause; })))));
        check(preparing.getCause() == prepareCause, "Prepare cause retained");
        equal("broken", preparing.getUnitId(), "Prepare failure identifies unit");
        code("prepare_failed", () -> runtime.prepare(Collections.singletonList(
                pack("p", "new", unit("null", strings(), () -> null)))));
        ContentRuntime.Candidate activation = runtime.prepare(Arrays.asList(
                pack("p", "new", probe.unit("staged", strings())),
                pack("z-fails", "1", unit("broken", strings(), () -> scope -> {
                    check(oldScope.isActive(), "Old scope remains active during all staging");
                    check(!scope.isActive(), "Failing scope is inactive");
                    probe.register("partial", scope);
                    throw new Exception("activation failure");
                }))));
        ContentException activating = code("activation_failed", () -> runtime.apply(activation));
        equal("z-fails", activating.getPackId(), "Activation failure identifies pack");
        equal("broken", activating.getUnitId(), "Activation failure identifies unit");
        code("stale_candidate", () -> runtime.apply(activation));
        equal(generation, runtime.snapshot().get(0).getGeneration(), "Failed staging preserves generation");
        equal("old", runtime.snapshot().get(0).getVersion(), "Failed staging preserves old version");
        check(oldScope.isActive(), "Old callbacks remain active after rollback");
        equal(1, probe.resources, "All staged resources rolled back");
        for (int i = 1; i < probe.scopes.size(); i++) {
            check(!probe.scopes.get(i).isActive(), "Rolled-back callbacks cannot execute");
        }
        code("activation_failed", () -> runtime.apply(runtime.prepare(Collections.singletonList(
                pack("p", "closed-scope", unit("closed", strings(), () -> ResourceScope::close))))));
        check(oldScope.isActive(), "A prematurely closed staged scope cannot deactivate the old generation");
        runtime.close();
        equal(0, probe.resources, "Old generation remains cleanable");
    }

    private static void candidatesAreBoundToRuntimeAndRevision() {
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        ContentRuntime foreign = new ContentRuntime(ANY_THREAD);
        PackDefinition first = pack("p", "1", emptyUnit("u"));
        ContentRuntime.Candidate candidate = runtime.prepare(Collections.singletonList(first));
        equal(0L, runtime.getRevision(), "Preparing a candidate does not advance the visible revision");
        code("stale_candidate", () -> foreign.apply(candidate));
        runtime.apply(candidate);
        code("stale_candidate", () -> runtime.apply(candidate));
        equal(1L, runtime.getRevision(), "Only the committed candidate advances the revision");
        ContentRuntime.Candidate stale = runtime.prepare(Collections.singletonList(pack("p", "2", emptyUnit("u"))));
        ContentRuntime.Candidate current = runtime.prepare(Collections.singletonList(pack("p", "3", emptyUnit("u"))));
        runtime.apply(current);
        code("stale_candidate", () -> runtime.apply(stale));
        ContentRuntime.Candidate beforeUnload = runtime.prepare(Collections.singletonList(first));
        runtime.deactivate(strings("p"));
        code("stale_candidate", () -> runtime.apply(beforeUnload));
        equal(3L, runtime.getRevision(), "Removing the last pack still advances the visible revision");
        runtime.apply(runtime.prepare(Collections.singletonList(first)));
        ContentRuntime.Candidate survivesFailure = runtime.prepare(Collections.singletonList(first));
        code("activation_failed", () -> runtime.apply(runtime.prepare(Collections.singletonList(
                pack("p", "bad", unit("u", strings(), () -> scope -> { throw new Exception("fail"); }))))));
        equal(4L, runtime.getRevision(), "Failed activation preserves the visible revision");
        runtime.apply(survivesFailure);
        runtime.close();
        runtime.close();
        code("runtime_closed", () -> runtime.prepare(Collections.singletonList(first)));
        code("runtime_closed", () -> runtime.apply(candidate));
        foreign.close();
    }

    private static void cleanupFailureAfterCommitIsExplicit() {
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        AtomicReference<ResourceScope> oldScope = new AtomicReference<ResourceScope>();
        AtomicReference<ResourceScope> newScope = new AtomicReference<ResourceScope>();
        AtomicInteger closed = new AtomicInteger();
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "1", unit("u", strings(), () -> scope -> {
            oldScope.set(scope);
            scope.own(() -> {
                check(!scope.isActive(), "Old callbacks disabled before failing cleanup");
                closed.incrementAndGet();
                throw new Exception("old cleanup");
            });
        })))));
        ContentRuntime.Candidate waiting = runtime.prepare(Collections.singletonList(pack("other", "1", emptyUnit("u"))));
        long oldGeneration = runtime.snapshot().get(0).getGeneration();
        ContentException failure = code("cleanup_failed", () -> runtime.apply(runtime.prepare(Collections.singletonList(
                pack("p", "2", unit("u", strings(), () -> scope -> {
                    newScope.set(scope);
                    scope.own(closed::incrementAndGet);
                }))))));
        equal("p", failure.getPackId(), "Cleanup failure identifies pack");
        equal("2", runtime.snapshot().get(0).getVersion(), "Cleanup failure after commit retains new version");
        check(runtime.snapshot().get(0).getGeneration() > oldGeneration, "Generation committed before cleanup failure");
        check(!oldScope.get().isActive(), "Old callbacks stay disabled after cleanup failure");
        check(newScope.get().isActive(), "Committed callbacks remain active");
        code("cleanup_failed", () -> runtime.prepare(Collections.singletonList(pack("x", "1", emptyUnit("u")))));
        code("cleanup_failed", () -> runtime.apply(waiting));
        runtime.deactivate(strings("p"));
        check(!newScope.get().isActive(), "Unhealthy runtime still allows deactivation");
        equal(2, closed.get(), "Unhealthy runtime can release its committed resources");
        runtime.close();
    }

    private static void rollbackCleanupFailureDisablesActivation() {
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        AtomicReference<ResourceScope> staged = new AtomicReference<ResourceScope>();
        ContentException failure = code("activation_failed", () -> runtime.apply(runtime.prepare(Collections.singletonList(
                pack("p", "1", unit("u", strings(), () -> scope -> {
                    staged.set(scope);
                    scope.own(() -> { throw new Exception("rollback cleanup"); });
                    throw new Exception("staging failed");
                }))))));
        equal(1, failure.getSuppressed().length, "Rollback cleanup failure is attached to activation failure");
        check(!staged.get().isActive(), "Failed rollback never enables staged callbacks");
        check(runtime.snapshot().isEmpty(), "Failed activation does not commit");
        code("cleanup_failed", () -> runtime.prepare(Collections.singletonList(pack("p", "2", emptyUnit("u")))));
        runtime.close();
    }

    private static void closeCleansEveryPackAfterFailure() {
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        List<String> closed = new ArrayList<String>();
        List<ResourceScope> scopes = new ArrayList<ResourceScope>();
        PackDefinition base = pack("base", "1", unit("u", strings(), () -> scope -> {
            scopes.add(scope);
            scope.own(() -> {
                closed.add("base");
                throw new Exception("base cleanup");
            });
        }));
        PackDefinition top = new PackDefinition("top", "1", strings("base"), Collections.singletonList(
                unit("u", strings(), () -> scope -> {
                    scopes.add(scope);
                    scope.own(() -> {
                        for (ResourceScope owned : scopes) {
                            check(!owned.isActive(), "All packs are inactive before any close callback");
                        }
                        closed.add("top");
                        throw new Exception("top cleanup");
                    });
                })));
        runtime.apply(runtime.prepare(Arrays.asList(top, base)));
        ContentException failure = code("cleanup_failed", runtime::close);
        equal(1, failure.getSuppressed().length, "Close aggregates failed packs");
        equal(Arrays.asList("top", "base"), closed, "Close continues in reverse dependency order");
        check(runtime.snapshot().isEmpty(), "Closed runtime exposes no active generations");
        runtime.close();
        equal(2, closed.size(), "Runtime close is idempotent after failure");
    }

    private static void snapshotsAreDetachedAndImmutable() {
        Probe probe = new Probe();
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "1", probe.unit("old", strings())))));
        List<ContentRuntime.PackSnapshot> first = runtime.snapshot();
        ContentRuntime.PackSnapshot metadata = first.get(0);
        equal(1, metadata.getResourceCount(), "Snapshot captures resource count");
        expect(UnsupportedOperationException.class, first::clear);
        expect(UnsupportedOperationException.class, () -> metadata.getUnitIds().clear());
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "2", probe.unit("new", strings())))));
        equal("1", metadata.getVersion(), "Old snapshot is detached from replacement");
        equal(Collections.singletonList("old"), metadata.getUnitIds(), "Old unit list is detached");
        equal(1, metadata.getResourceCount(), "Old resource count is immutable");
        check(runtime.snapshot().get(0).getGeneration() > metadata.getGeneration(), "Replacements advance generation");
        runtime.deactivate(strings("p"));
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "3", emptyUnit("u")))));
        check(runtime.snapshot().get(0).getGeneration() > metadata.getGeneration(), "Unload and re-add never reuse generation");
        runtime.close();
        equal(1, first.size(), "Snapshot survives close");
    }

    private static void injectedThreadGuardAndConcurrentPreparation() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicInteger guards = new AtomicInteger();
        Runnable guard = () -> {
            guards.incrementAndGet();
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("wrong thread");
            }
        };
        ContentRuntime runtime = new ContentRuntime(guard);
        ContentRuntime.Candidate candidate = runtime.prepare(Collections.singletonList(pack("p", "1", emptyUnit("u"))));
        AtomicReference<Throwable> unexpected = new AtomicReference<Throwable>();
        Thread wrongThread = new Thread(() -> {
            try {
                expect(IllegalStateException.class, () -> runtime.apply(candidate));
                expect(IllegalStateException.class, () -> runtime.deactivate(strings()));
                expect(IllegalStateException.class, runtime::close);
                check(runtime.snapshot().isEmpty(), "Wrong-thread operations do not mutate runtime");
            } catch (Throwable failure) {
                unexpected.set(failure);
            }
        }, "runtime-wrong-thread-test");
        wrongThread.start();
        wrongThread.join();
        rethrow(unexpected.get());
        equal(3, guards.get(), "Every mutation delegates to the injected guard");
        runtime.apply(candidate);

        CountDownLatch preparing = new CountDownLatch(1);
        CountDownLatch continuePreparing = new CountDownLatch(1);
        AtomicReference<ContentRuntime.Candidate> backgroundCandidate = new AtomicReference<ContentRuntime.Candidate>();
        Thread preparer = new Thread(() -> {
            try {
                backgroundCandidate.set(runtime.prepare(Collections.singletonList(pack("p", "background",
                        unit("u", strings(), () -> {
                            preparing.countDown();
                            continuePreparing.await();
                            return scope -> { };
                        })))));
            } catch (Throwable failure) {
                unexpected.set(failure);
                preparing.countDown();
            }
        }, "runtime-prepare-test");
        preparer.start();
        preparing.await();
        try {
            runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "foreground", emptyUnit("u")))));
        } finally {
            continuePreparing.countDown();
        }
        preparer.join();
        rethrow(unexpected.get());
        code("stale_candidate", () -> runtime.apply(backgroundCandidate.get()));
        equal("foreground", runtime.snapshot().get(0).getVersion(), "Concurrent prepare captures its initial revision");
        runtime.close();
    }

    private static void repeatedReplacementRestoresBaseline() {
        Probe probe = new Probe();
        ContentRuntime runtime = new ContentRuntime(ANY_THREAD);
        runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "initial",
                probe.unit("a", strings()), probe.unit("b", strings("a"))))));
        long generation = runtime.snapshot().get(0).getGeneration();
        for (int i = 0; i < 100; i++) {
            runtime.apply(runtime.prepare(Collections.singletonList(pack("p", Integer.toString(i),
                    probe.unit("a", strings()), probe.unit("b", strings("a"))))));
            equal(2, probe.resources, "Replacement retains exactly one generation at iteration " + i);
            long nextGeneration = runtime.snapshot().get(0).getGeneration();
            check(nextGeneration > generation, "Every repeated replacement advances generation");
            generation = nextGeneration;
            if (i % 10 == 9) {
                runtime.deactivate(strings("p"));
                equal(0, probe.resources, "Repeated unload restores registration baseline");
                runtime.apply(runtime.prepare(Collections.singletonList(pack("p", "reloaded",
                        probe.unit("a", strings()), probe.unit("b", strings("a"))))));
                equal(2, probe.resources, "Reload registers exactly one generation");
                generation = runtime.snapshot().get(0).getGeneration();
            }
        }
        runtime.close();
        equal(0, probe.resources, "Final close restores registration baseline");
        for (ResourceScope scope : probe.scopes) {
            check(!scope.isActive(), "No callback from any old generation remains active");
            equal(0, scope.size(), "No old scope retains resources");
        }
    }

    private static ContentUnit emptyUnit(String id) {
        return unit(id, strings(), () -> scope -> { });
    }

    private static ContentUnit unit(String id, Collection<String> dependencies, Preparation preparation) {
        return new ContentUnit() {
            @Override
            public String getId() { return id; }
            @Override
            public Collection<String> getDependencies() { return dependencies; }
            @Override
            public PreparedUnit prepare() throws Exception { return preparation.prepare(); }
        };
    }

    private static PackDefinition pack(String id, String version, ContentUnit... units) {
        return new PackDefinition(id, version, strings(), Arrays.asList(units));
    }

    private static List<String> strings(String... strings) {
        return Arrays.asList(strings);
    }

    private static List<ContentUnit> noUnits() {
        return Collections.emptyList();
    }

    private static ContentException code(String expected, Action action) {
        ContentException failure = expect(ContentException.class, action);
        equal(expected, failure.getCode(), "Stable error code");
        return failure;
    }

    private static <T extends Throwable> T expect(Class<T> type, Action action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Expected " + type.getSimpleName() + " but received " + failure, failure);
            }
            return type.cast(failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure != null) {
            throw new AssertionError("Background thread failed", failure);
        }
    }

    @FunctionalInterface
    private interface Preparation {
        PreparedUnit prepare() throws Exception;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static final class Probe {
        private final List<String> events = new ArrayList<String>();
        private final List<ResourceScope> scopes = new ArrayList<ResourceScope>();
        private int resources;

        private ContentUnit unit(String id, Collection<String> dependencies) {
            return RuntimeContractTest.unit(id, dependencies, () -> {
                events.add("prepare:" + id);
                return scope -> register(id, scope);
            });
        }

        private void register(String id, ResourceScope scope) {
            check(!scope.isActive(), "Resources are registered in inactive scopes");
            events.add("activate:" + id);
            scopes.add(scope);
            resources++;
            scope.own(() -> {
                check(!scope.isActive(), "Callbacks are disabled before cleanup");
                events.add("close:" + id);
                resources--;
            });
        }
    }
}
