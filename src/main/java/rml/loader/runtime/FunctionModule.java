package rml.loader.runtime;

import com.google.gson.JsonElement;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;
import rml.loader.api.event.FunctionLoadEvent;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.function.FunctionExecutor;
import rml.loader.content.ContentProviders;
import rml.loader.content.CompiledResource;
import rml.loader.content.data.DataContext;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.runtime.ContentRuntime;
import rml.runtime.ContentException;
import rml.runtime.ContentUnit;
import rml.runtime.OwnedRegistry;
import rml.runtime.PackDefinition;
import rml.runtime.PreparedUnit;
import rml.runtime.ResourceScope;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** World-session adapter for native functions and compiled runtime content. */
public final class FunctionModule implements AutoCloseable {
    private final PackRegistry packs;
    private final ModuleLoader modules;
    private final ContentCatalog content;
    private final ContentProviders providers;
    private final CompiledTagModule tags;
    private final CompiledFunctionEngine engine;
    private final Logger logger;
    private Session session;
    private String lastError;

    FunctionModule(PackRegistry packs, ModuleLoader modules, ContentCatalog content, ContentProviders providers,
                   CompiledTagModule tags, CompiledFunctionEngine engine, Logger logger) {
        this.packs = packs;
        this.modules = modules;
        this.content = content;
        this.providers = providers;
        this.tags = tags;
        this.engine = engine;
        this.logger = logger;
    }

    public void onLoad(FunctionLoadEvent event) {
        try {
            if (session == null || session.manager != event.functionManager) {
                close();
                session = new Session(event.functionManager, event.functions);
            } else {
                session.functions.rebind(event.functions);
            }
            reload(null);
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            logger.error("RML content candidate was rejected; inspect /rml functions status", e);
        }
    }

    public void reload(String packId) {
        CheckResult checked = check(packId);
        apply(checked.getCandidateId(), checked.getRevision());
    }

    /** Prepares one detached candidate; a subsequent check supersedes the previous one. */
    public CheckResult check(String packId) {
        Session current = requireSession();
        current.checkThread();
        current.checked = null;
        ModuleType functions = ModuleType.valueOf(new ResourceLocation("rml", "functions"));
        ModuleType compiledType = ModuleType.valueOf(ContentCatalog.MODULE);
        Map<String, ContainerHolder> selected = new TreeMap<>();
        for (ContainerHolder pack : packs.all()) {
            String id = pack.getContainer().getModId();
            if ((pack.hasModule(functions) || pack.hasModule(compiledType)) && (packId == null || packId.equals(id))) {
                selected.put(id, pack);
            }
        }
        if (packId != null && selected.isEmpty()) throw new IllegalArgumentException("Pack has no runtime content: " + packId);
        try {
            ContentCatalog.Snapshot compiled = content.read(packId);
            requireUnchangedStartup(compiled, selected.keySet());
            Map<String, List<ContainerHolder.ModuleConsumeContext>> sources = new TreeMap<>();
            for (String id : selected.keySet()) sources.put(id, new ArrayList<>());
            for (ContainerHolder.ModuleConsumeContext source : modules.read(functions, FunctionModule.class, packId)) {
                List<ContainerHolder.ModuleConsumeContext> files = sources.get(source.getContainerHolder().getContainer().getModId());
                if (files != null && source.isExtension("mcfunction", "executor")) files.add(source);
            }
            Set<ResourceLocation> available = new HashSet<>();
            for (ResourceLocation name : current.manager.getFunctions().keySet()) {
                if (!current.scheduler.belongsTo(name, selected.keySet())) available.add(name);
            }
            for (List<ContainerHolder.ModuleConsumeContext> files : sources.values()) {
                for (ContainerHolder.ModuleConsumeContext source : files) {
                    if (source.isExtension("mcfunction")) declare(available, source.getResourceLocation());
                }
            }
            for (String id : selected.keySet()) {
                for (CompiledResource resource : compiled.resourcesForPack(id)) {
                    if ("function".equals(resource.getType())) declare(available, resource.getId());
                }
            }
            List<PackDefinition> definitions = new ArrayList<>();
            for (Map.Entry<String, ContainerHolder> entry : selected.entrySet()) {
                List<ContentUnit> units = new ArrayList<>();
                for (ContainerHolder.ModuleConsumeContext source : sources.get(entry.getKey())) {
                    units.add(new SourceUnit(current, source, available));
                }
                for (CompiledResource resource : compiled.resourcesForPack(entry.getKey())) {
                    if (!providers.isStartup(resource.getType())) {
                        units.add(new CompiledUnit(current, resource, available));
                    }
                }
                Set<String> dependencies = new LinkedHashSet<>();
                for (ModuleType type : new ModuleType[]{functions, compiledType}) {
                    Module declaration = entry.getValue().getModules().get(type);
                    if (declaration != null) dependencies.addAll(declaration.getDependencies());
                }
                definitions.add(new PackDefinition(entry.getKey(), entry.getValue().getContainer().getVersion(), dependencies, units));
            }
            ContentRuntime.Candidate prepared = current.runtime.prepare(definitions);
            current.checked = new CheckResult(prepared, current.runtime.getRevision(), definitions);
            lastError = null;
            return current.checked;
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            throw e;
        }
    }

    /** Commits exactly the checked content, without reading its source files again. */
    public void apply(String candidateId, long expectedRevision) {
        Session current = requireSession();
        current.checkThread();
        CheckResult checked = current.checked;
        if (checked == null || !checked.candidateId.equals(candidateId))
            throw new ContentException("stale_candidate", null, null, "No matching checked candidate in this server session");
        requireRevision(current, expectedRevision);
        try {
            current.runtime.apply(checked.candidate);
            lastError = null;
        } catch (RuntimeException failure) {
            lastError = failure.getMessage();
            throw failure;
        } finally {
            current.checked = null;
        }
    }

    public void deactivate(String sessionId, long expectedRevision, Collection<String> packIds) {
        Session current = requireSession();
        current.checkThread();
        if (!current.id.equals(sessionId))
            throw new ContentException("stale_session", null, null, "The server session has changed");
        requireRevision(current, expectedRevision);
        try {
            current.runtime.deactivate(packIds);
            lastError = null;
        } finally {
            current.checked = null;
        }
    }

    private static void requireRevision(Session current, long expectedRevision) {
        if (current.runtime.getRevision() != expectedRevision)
            throw new ContentException("revision_conflict", null, null, "Expected revision " + expectedRevision
                    + " but active revision is " + current.runtime.getRevision());
    }

    private void requireUnchangedStartup(ContentCatalog.Snapshot candidate, Set<String> packIds) {
        ContentCatalog.Snapshot loaded = content.snapshot();
        for (String packId : packIds) {
            Map<String, CompiledResource> previous = new TreeMap<>();
            for (CompiledResource resource : loaded.resourcesForPack(packId)) {
                if (providers.isStartup(resource.getType())) previous.put(resource.getType() + "/" + resource.getId(), resource);
            }
            for (CompiledResource resource : candidate.resourcesForPack(packId)) {
                if (!providers.isStartup(resource.getType())) continue;
                String key = resource.getType() + "/" + resource.getId();
                CompiledResource original = previous.remove(key);
                if (original == null || !original.getValue().equals(resource.getValue()))
                    throw new ContentException("requires_restart", packId, key, "Startup definition changed at " + resource.getSource());
            }
            if (!previous.isEmpty()) {
                String key = previous.keySet().iterator().next();
                throw new ContentException("requires_restart", packId, key, "Startup definition was removed: " + key);
            }
        }
    }

    public String getSessionId() { return session == null ? null : session.id; }

    public long getRevision() {
        Session current = requireSession();
        current.checkThread();
        return current.runtime.getRevision();
    }

    public CheckResult getCheckedCandidate() { return session == null ? null : session.checked; }

    public static final class CheckResult {
        private final String candidateId = UUID.randomUUID().toString();
        private final ContentRuntime.Candidate candidate;
        private final long revision;
        private final Map<String, List<String>> units;

        private CheckResult(ContentRuntime.Candidate candidate, long revision, List<PackDefinition> definitions) {
            this.candidate = candidate;
            this.revision = revision;
            Map<String, List<String>> units = new TreeMap<>();
            for (PackDefinition pack : definitions) {
                List<String> ids = new ArrayList<>();
                for (ContentUnit unit : pack.getUnits()) ids.add(unit.getId());
                Collections.sort(ids);
                units.put(pack.getId(), Collections.unmodifiableList(ids));
            }
            this.units = Collections.unmodifiableMap(units);
        }

        public String getCandidateId() { return candidateId; }
        public long getRevision() { return revision; }
        public Map<String, List<String>> getUnits() { return units; }
    }

    public void deactivate(String packId) {
        Session current = requireSession();
        deactivate(current.id, current.runtime.getRevision(), Collections.singleton(packId));
    }

    public void schedule(ResourceLocation function, String delay, boolean replace) {
        Session current = requireSession();
        current.checkThread();
        current.scheduler.schedule(function, delay, replace);
    }

    public int clearSchedule(ResourceLocation function) {
        Session current = requireSession();
        current.checkThread();
        return current.scheduler.clear(function);
    }

    public int invoke(ResourceLocation name, DataContext context) {
        Session current = requireSession();
        current.checkThread();
        FunctionObject function = current.manager.getFunction(name);
        if (function == null) throw new IllegalArgumentException("Missing data function: " + name);
        ICommandSender sender = new DataCommandSender(context);
        net.minecraft.nbt.NBTTagCompound inputs = context.getInputs();
        if (inputs != null) sender = rml.loader.runtime.function.FunctionCommandContext.withArguments(sender, inputs);
        return engine.invoke(current.manager, function, sender);
    }
    public void tick() {
        if (session != null) {
            session.checkThread();
            session.scheduler.tick();
        }
    }

    public List<ContentRuntime.PackSnapshot> snapshot() {
        return session == null ? Collections.emptyList() : session.runtime.snapshot();
    }

    public String getLastError() { return lastError; }

    @Override public void close() {
        if (session != null) {
            Session previous = session;
            previous.checkThread();
            try {
                previous.runtime.close();
            } finally {
                previous.scheduler.close();
                session = null;
            }
        }
        lastError = null;
    }

    private Session requireSession() {
        if (session == null) throw new ContentException("session_not_ready", null, null, "The server function manager is not ready");
        return session;
    }

    private static void declare(Set<ResourceLocation> available, ResourceLocation name) {
        if (!available.add(name)) throw new IllegalArgumentException("Conflicting function: " + name);
    }

    private static WorldServer world() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getWorld(0);
    }

    private PreparedUnit function(Session target, String pack, ResourceLocation name, List<String> lines, boolean compiled) {
        FunctionObject function = compiled ? engine.compile(target.manager, name, lines) : FunctionObject.create(target.manager, lines);
        return scope -> {
            if (compiled) engine.bind(function, scope);
            scope.own(target.functions.register(name, function));
            scope.own(target.scheduler.bind(name, scope, pack));
        };
    }

    private final class SourceUnit implements ContentUnit {
        private final Session target;
        private final ContainerHolder.ModuleConsumeContext source;
        private final Set<ResourceLocation> available;

        private SourceUnit(Session target, ContainerHolder.ModuleConsumeContext source, Set<ResourceLocation> available) {
            this.target = target;
            this.source = source;
            this.available = available;
        }

        @Override public String getId() { return source.getRelativePath(); }
        @Override public Collection<String> getDependencies() { return Collections.emptyList(); }

        @Override public PreparedUnit prepare() throws Exception {
            if (source.isExtension("mcfunction")) {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = source.openBufferedReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) lines.add(line);
                }
                return function(target, source.getContainerHolder().getContainer().getModId(), source.getResourceLocation(), lines, false);
            }
            FunctionExecutor[] executors = source.deserialize(FunctionExecutor[].class);
            for (FunctionExecutor executor : executors) {
                if (executor == null || !available.contains(executor.getFunction())) {
                    throw new IllegalArgumentException("Missing function in " + source.getUri());
                }
            }
            return scope -> {
                FunctionDispatcher dispatcher = scope.own(new FunctionDispatcher(scope, executors,
                        source.getContainerHolder().getContainer().getModId() + "/" + source.getRelativePath(), logger));
                dispatcher.register();
            };
        }
    }

    private final class CompiledUnit implements ContentUnit {
        private final Session target;
        private final CompiledResource source;
        private final Set<ResourceLocation> available;

        private CompiledUnit(Session target, CompiledResource source, Set<ResourceLocation> available) {
            this.target = target;
            this.source = source;
            this.available = available;
        }

        @Override public String getId() { return "content/" + source.getType() + "/" + source.getId(); }
        @Override public Collection<String> getDependencies() { return Collections.emptyList(); }

        @Override public PreparedUnit prepare() {
            switch (source.getType()) {
                case "function":
                    List<String> lines = new ArrayList<>();
                    for (JsonElement command : source.getValue().getAsJsonArray()) lines.add(command.getAsString());
                    return function(target, source.getPackId(), source.getId(), lines, true);
                case "function_tag":
                    for (JsonElement member : source.getValue().getAsJsonArray()) {
                        if (!available.contains(new ResourceLocation(member.getAsString()))) {
                            throw new IllegalArgumentException("Missing function in " + source.getSource() + ": " + member);
                        }
                    }
                    PreparedUnit metadata = tags.prepare(source);
                    boolean magic = "minecraft:load".equals(source.getId().toString()) || "minecraft:tick".equals(source.getId().toString());
                    ResourceLocation wrapper = new ResourceLocation(source.getPackId(), "__rml/tags/"
                            + source.getId().getNamespace() + "/" + source.getId().getPath());
                    if (magic && !available.contains(wrapper)) throw new IllegalArgumentException("Missing tag wrapper: " + wrapper);
                    return scope -> {
                        metadata.activate(scope);
                        if (magic) {
                            FunctionTagDispatcher dispatcher = scope.own(new FunctionTagDispatcher(scope, target.manager,
                                    FunctionModule::world, source.getId(), wrapper));
                            dispatcher.register();
                        }
                    };
                default:
                    if (source.getType().startsWith("tag/")) return tags.prepare(source);
                    return providers.prepare(source);
            }
        }
    }

    private final class Session {
        private final Thread owner = Thread.currentThread();
        private final FunctionManager manager;
        private final OwnedRegistry<ResourceLocation, FunctionObject> functions;
        private final ContentRuntime runtime;
        private final FunctionSchedule scheduler;
        private final String id = UUID.randomUUID().toString();
        private CheckResult checked;

        private Session(FunctionManager manager, Map<ResourceLocation, FunctionObject> functions) {
            this.manager = manager;
            this.functions = new OwnedRegistry<>(functions);
            this.runtime = new ContentRuntime(this::checkThread);
            this.scheduler = new FunctionSchedule(manager, FunctionModule::world, logger);
        }

        private void checkThread() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Content changes must run on the owning server thread");
        }
    }
}
