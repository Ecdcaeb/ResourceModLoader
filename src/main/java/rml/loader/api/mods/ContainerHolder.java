package rml.loader.api.mods;

import com.google.gson.JsonElement;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLContainerHolder;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;
import rml.deserializer.JsonDeserializeException;
import rml.io.PackFile;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.utils.file.JsonHelper;
import rml.loader.deserialize.Deserializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable Forge identity and declarations. Registration does not execute content. */
@PublicAPI
public final class ContainerHolder implements FMLContainerHolder {
    private final ModContainer container;
    private final Map<ModuleType, Module> modules;
    private final int packVersion;

    public ContainerHolder(ModContainer container, Module[] modules) {
        this(container, modules, 3);
    }

    public ContainerHolder(ModContainer container, Module[] modules, int packVersion) {
        this.container = Objects.requireNonNull(container, "container");
        this.packVersion = packVersion;
        Map<String, Module> ordered = new TreeMap<>();
        for (Module module : Objects.requireNonNull(modules, "modules")) {
            Objects.requireNonNull(module, "module");
            if (ordered.put(module.getModuleType().getName().toString(), module) != null) {
                throw new IllegalArgumentException("Duplicate module in " + container.getModId());
            }
        }
        Map<ModuleType, Module> declarations = new LinkedHashMap<>();
        for (Module module : ordered.values()) declarations.put(module.getModuleType(), module);
        this.modules = Collections.unmodifiableMap(declarations);
    }

    public ContainerHolder(ModContainer container) {
        this(container, ModuleType.getAllForDefault());
    }

    public ModContainer getContainer() { return container; }
    public Map<ModuleType, Module> getModules() { return modules; }
    public int getPackVersion() { return packVersion; }
    public boolean hasModule(ModuleType module) { return modules.containsKey(module); }

    @Override
    public ModContainer getFMLContainer() { return container; }

    @Override
    public String toString() {
        return "RML pack " + container.getModId() + " (format " + packVersion + ")";
    }

    /** Source snapshot that remains valid after its archive/directory reader closes. */
    public static final class ModuleConsumeContext {
        private final ContainerHolder containerHolder;
        private final Module module;
        private final PackFile file;
        private final Logger logger;

        public ModuleConsumeContext(Logger logger, ContainerHolder containerHolder, Module module, PackFile file) {
            this.logger = Objects.requireNonNull(logger, "logger");
            this.containerHolder = Objects.requireNonNull(containerHolder, "containerHolder");
            this.module = Objects.requireNonNull(module, "module");
            this.file = Objects.requireNonNull(file, "file");
        }

        public ContainerHolder getContainerHolder() { return containerHolder; }
        public Module getModule() { return module; }
        public URI getUri() { return file.getUri(); }
        public String getRelativePath() { return file.getRelativePath(); }

        public void error(Throwable throwable, String message, Object... args) {
            FormattedMessage detail = new FormattedMessage(message, args);
            String text = "RML " + containerHolder.getContainer().getModId() + " / "
                    + module.getModuleType().getName() + " / " + getRelativePath() + ": " + detail.getFormattedMessage();
            if (module.isForceLoaded()) throw new IllegalStateException(text, throwable);
            logger.error(text, throwable);
        }

        public void info(String message, Object... args) { logger.info(message, args); }

        public ResourceLocation getResourceLocation() {
            return new ResourceLocation(containerHolder.getContainer().getModId(),
                    FilenameUtils.removeExtension(getRelativePath()));
        }

        public String getExtension() { return FilenameUtils.getExtension(file.getPath()); }

        public boolean isExtension(String... extensions) {
            String extension = getExtension();
            for (String candidate : extensions) if (candidate.equals(extension)) return true;
            return false;
        }

        public InputStream openStream() { return file.openStream(); }
        public BufferedReader openBufferedReader() { return file.openReader(StandardCharsets.UTF_8); }
        public BufferedReader openBufferedReader(Charset charset) { return file.openReader(charset); }

        public JsonElement toJson() throws IOException, JsonDeserializeException {
            try (BufferedReader reader = openBufferedReader()) {
                return JsonHelper.parse(reader);
            }
        }

        public <T> T deserialize(Class<T> type) throws JsonDeserializeException, IOException {
            return Deserializer.decode(type, toJson());
        }

        public byte[] getBytes() { return file.getBytes(); }
    }
}
