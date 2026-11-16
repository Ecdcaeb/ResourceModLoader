package rml.loader.runtime;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rml.io.PackFile;
import rml.io.PackSource;
import rml.loader.api.event.RMLModuleLoadingEvent;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Reads detached module sources, then enters the Forge context only for consumers. */
public final class ModuleLoader {
    private final PackRegistry packs;
    private final Logger logger;

    public ModuleLoader(PackRegistry packs, Logger logger) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public List<ContainerHolder> containers(ModuleType type) {
        Objects.requireNonNull(type, "type");
        Map<String, ContainerHolder> ordered = new TreeMap<>();
        for (ContainerHolder pack : RMLModuleLoadingEvent.post(packs.withModule(type), type)) {
            if (!pack.hasModule(type)) {
                throw new IllegalArgumentException("Pack does not declare module " + type.getName());
            }
            String id = pack.getContainer().getModId();
            if (ordered.put(id, pack) != null) {
                throw new IllegalArgumentException("Duplicate module pack: " + id);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered.values()));
    }

    public void forEachContainer(ModuleType type, Consumer<ContainerHolder> consumer) {
        for (ContainerHolder pack : containers(type)) {
            inContainer(pack, () -> consumer.accept(pack));
        }
    }

    /** Strict source collection for transactional providers. Any I/O failure aborts preparation. */
    public List<ContainerHolder.ModuleConsumeContext> read(ModuleType type, Class<?> caller) {
        return read(type, LogManager.getLogger(caller), true, null);
    }

    public List<ContainerHolder.ModuleConsumeContext> read(ModuleType type, Class<?> caller, String packId) {
        return read(type, LogManager.getLogger(caller), true, packId);
    }

    /** Startup-only integrations retain their explicitly optional module error policy. */
    public void load(ModuleType type, Consumer<ContainerHolder.ModuleConsumeContext> consumer, Class<?> caller) {
        for (ContainerHolder.ModuleConsumeContext context : read(type, LogManager.getLogger(caller), false, null)) {
            inContainer(context.getContainerHolder(), () -> consumer.accept(context));
        }
    }

    private List<ContainerHolder.ModuleConsumeContext> read(ModuleType type, Logger consumerLogger, boolean strict, String packId) {
        List<ContainerHolder.ModuleConsumeContext> contexts = new ArrayList<>();
        for (ContainerHolder pack : containers(type)) {
            if (packId != null && !packId.equals(pack.getContainer().getModId())) continue;
            Module module = pack.getModules().get(type);
            String location = "assets/" + pack.getContainer().getModId() + "/" + module.getLocation();
            try (PackSource source = PackSource.open(pack.getContainer().getSource().toPath())) {
                for (PackFile file : source.read(location, !type.isFile())) {
                    contexts.add(new ContainerHolder.ModuleConsumeContext(consumerLogger, pack, module, file));
                }
            } catch (IOException | IllegalArgumentException e) {
                LoadException failure = new LoadException(pack.getContainer().getModId(), type.getName().toString(), location, e);
                if (strict || module.isForceLoaded()) throw failure;
                logger.error(failure.getMessage(), failure);
            }
        }
        return Collections.unmodifiableList(contexts);
    }

    private static void inContainer(ContainerHolder pack, Runnable action) {
        Loader loader = Loader.instance();
        ModContainer previous = loader.activeModContainer();
        loader.setActiveModContainer(pack.getContainer());
        try {
            action.run();
        } finally {
            loader.setActiveModContainer(previous);
        }
    }

    public static final class LoadException extends RuntimeException {
        private final String packId;
        private final String moduleId;
        private final String path;

        private LoadException(String packId, String moduleId, String path, Throwable cause) {
            super("Cannot read RML module " + moduleId + " in " + packId + " at " + path, cause);
            this.packId = packId;
            this.moduleId = moduleId;
            this.path = path;
        }

        public String getPackId() { return packId; }
        public String getModuleId() { return moduleId; }
        public String getPath() { return path; }
    }
}
