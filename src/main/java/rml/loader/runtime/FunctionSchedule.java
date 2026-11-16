package rml.loader.runtime;

import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.Logger;
import rml.runtime.OwnedRegistry;
import rml.runtime.ResourceScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Supplier;

/** Server-tick scheduler. Each generation owns one queue, not one scope entry per completed job. */
final class FunctionSchedule implements AutoCloseable {
    private final FunctionManager manager;
    private final Supplier<WorldServer> world;
    private final Logger logger;
    private final PriorityQueue<Job> jobs = new PriorityQueue<>((left, right) -> {
        int time = Long.compare(left.due, right.due);
        return time != 0 ? time : Long.compare(left.order, right.order);
    });
    private final Map<ResourceLocation, Set<Job>> byFunction = new HashMap<>();
    private final Map<ResourceLocation, Owner> owners = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, Owner> registrations = new OwnedRegistry<>(owners);
    private final Map<ResourceScope, Owner> scopes = new IdentityHashMap<>();
    private final Owner serverOwner = new Owner(null, "server");
    private long tick;
    private long sequence;
    private boolean closed;
    private WorldServer contextWorld;
    private ICommandSender sender;

    FunctionSchedule(FunctionManager manager, Supplier<WorldServer> world, Logger logger) {
        this.manager = manager;
        this.world = world;
        this.logger = logger;
    }

    AutoCloseable bind(ResourceLocation function, ResourceScope scope, String pack) {
        Owner owner = scopes.get(scope);
        if (owner == null) {
            owner = new Owner(scope, pack);
            scopes.put(scope, owner);
            scope.own(owner);
        }
        return registrations.register(function, owner);
    }

    boolean belongsTo(ResourceLocation function, Set<String> packs) {
        Owner owner = owners.get(function);
        return owner != null && packs.contains(owner.pack);
    }

    void schedule(ResourceLocation function, String delay, boolean replace) {
        if (closed) throw new IllegalStateException("Function scheduler is closed");
        if (manager.getFunction(function) == null) throw new IllegalArgumentException("Unknown function: " + function);
        long ticks = delay(delay);
        if (ticks > Long.MAX_VALUE - tick) throw new IllegalArgumentException("Schedule time is too large");
        if (replace) clear(function);
        Owner owner = owners.get(function);
        if (owner == null) owner = serverOwner;
        if (!owner.active()) throw new IllegalStateException("Function generation is inactive: " + function);
        Job job = new Job(function, tick + ticks, sequence++, owner);
        jobs.add(job);
        owner.pending.add(job);
        Set<Job> pending = byFunction.get(function);
        if (pending == null) {
            pending = new HashSet<>();
            byFunction.put(function, pending);
        }
        pending.add(job);
    }

    int clear(ResourceLocation function) {
        Set<Job> pending = byFunction.remove(function);
        if (pending == null) return 0;
        for (Job job : pending) {
            jobs.remove(job);
            job.owner.pending.remove(job);
        }
        return pending.size();
    }

    void tick() {
        if (closed) return;
        tick++;
        WorldServer currentWorld = world.get();
        if (currentWorld == null) return;
        if (currentWorld != contextWorld) {
            contextWorld = currentWorld;
            sender = new FunctionCommandSender(currentWorld, "schedule");
        }
        while (!jobs.isEmpty() && jobs.peek().due <= tick) {
            Job job = jobs.remove();
            job.owner.pending.remove(job);
            removeIndex(job);
            if (!job.owner.active()) continue;
            FunctionObject function = manager.getFunction(job.function);
            if (function == null) logger.error("Scheduled RML function is unavailable: {}", job.function);
            else manager.execute(function, sender);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        jobs.clear();
        byFunction.clear();
        serverOwner.pending.clear();
        contextWorld = null;
        sender = null;
    }

    private void removeIndex(Job job) {
        Set<Job> pending = byFunction.get(job.function);
        if (pending != null) {
            pending.remove(job);
            if (pending.isEmpty()) byFunction.remove(job.function);
        }
    }

    private static long delay(String text) {
        if (!text.matches("[0-9]+(?:\\.[0-9]+)?[tsd]?")) throw new IllegalArgumentException("Invalid schedule delay: " + text);
        char suffix = text.charAt(text.length() - 1);
        int multiplier = suffix == 's' ? 20 : suffix == 'd' ? 24000 : 1;
        String number = suffix >= '0' && suffix <= '9' ? text : text.substring(0, text.length() - 1);
        try {
            return Math.max(1, new BigDecimal(number).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Schedule delay is too large: " + text, e);
        }
    }

    private final class Owner implements AutoCloseable {
        private final ResourceScope scope;
        private final String pack;
        private final Set<Job> pending = new HashSet<>();
        private boolean live = true;

        private Owner(ResourceScope scope, String pack) { this.scope = scope; this.pack = pack; }
        private boolean active() { return live && (scope == null || scope.isActive()); }

        @Override public void close() {
            if (!live) return;
            live = false;
            Iterator<Job> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Job job = iterator.next();
                iterator.remove();
                jobs.remove(job);
                removeIndex(job);
            }
            scopes.remove(scope);
        }
    }

    private static final class Job {
        private final ResourceLocation function;
        private final long due;
        private final long order;
        private final Owner owner;

        private Job(ResourceLocation function, long due, long order, Owner owner) {
            this.function = function;
            this.due = due;
            this.order = order;
            this.owner = owner;
        }
    }
}
