package rml.loader.api.mods;

import com.google.gson.JsonElement;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLContainerHolder;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;
import net.minecraftforge.fml.common.ModContainer;
import rml.loader.api.utils.file.JsonHelper;
import rml.loader.deserialize.Deserializer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@PublicAPI
public class ContainerHolder implements FMLContainerHolder {
    public final ModContainer container;
    public final HashMap<ModuleType, Module> modules;
    public final int packVersion;
    public ContainerHolder(ModContainer container, Module[] modules){
        this(container, modules, 2);
    }

    public ContainerHolder(ModContainer container, Module[] modules, int packVersion){
        this.container = container;
        this.modules = new HashMap<>();
        for(Module module : modules){
            this.modules.put(module.moduleType, module);
        }
        this.packVersion = packVersion;
    }

    public ModContainer getContainer() {
        return container;
    }

    public HashMap<ModuleType,Module> getModules() {
        return modules;
    }

    public boolean hasModule(ModuleType module){
        return modules.containsKey(module) && modules.get(module) != null;
    }

    public ContainerHolder(ModContainer container){
        this(container, ModuleType.getAllForDefault());
    }

    @Override
    public ModContainer getFMLContainer() {
        return getContainer();
    }

    @Override
    public String toString() {
        return "ContainerHolder{" +
                "container=" + container +
                ", modules=" + modules +
                ", packVersion=" + packVersion +
                '}';
    }

    public static class ModuleConsumeContext {
        private final ContainerHolder containerHolder;
        private final Module module;
        private final Path root;
        private final Path file;
        private Logger logger;
        public ModuleConsumeContext(Logger logger, ContainerHolder containerHolder, Module module, Path root, Path file) {
            this.containerHolder = containerHolder;
            this.module = module;
            this.root = root;
            this.file = file;
            this.logger = logger;
        }

        public ContainerHolder getContainerHolder() {
            return containerHolder;
        }

        public Module getModule() {
            return module;
        }

        public Path getFile() {
            return file;
        }

        public Path getRoot() {
            return root;
        }

        public static void runThrow(Throwable throwable, String msg, Object... args){
            throw new RuntimeException(new FormattedMessage("Error at loading RML Module. " + msg, args).getFormattedMessage(), throwable);
        }

        public void error(Throwable throwable, String msg, Object... args){
            error(logger, throwable, msg, args);
        }

        public void error(Logger logger, Throwable throwable, String msg, Object... args){
            if (this.getModule().isForceLoaded()) runThrow(throwable, "Error at load module " + this.getModule() + "  " + msg, args);
            else logger.error(new FormattedMessage("Error at load module " + this.getModule() + "  " + msg, args).getFormattedMessage(), throwable);
        }

        public void info(String msg, Object... args) {
            logger.info(msg, args);
        }

        public String getRelativePath() {
            return getRoot().relativize(getFile()).toString();
        }

        public ResourceLocation getResourceLocation() {
            String name = FilenameUtils.removeExtension(getRelativePath()).replaceAll("\\\\", "/");
            return new ResourceLocation(getContainerHolder().getContainer().getModId(), name);
        }

        public String getExtension(){
            return FilenameUtils.getExtension(getFile().toString());
        }

        public boolean isExtension(String... str) {
            String ext = getExtension();
            for (String st : str) {
                if (st.equals(ext)) {
                    return true;
                }
            }
            return false;
        }

        public InputStream openStream() throws IOException {
            return Files.newInputStream(this.getFile());
        }

        public BufferedReader openBufferedReader() throws IOException {
            return Files.newBufferedReader(this.getFile());
        }

        public BufferedReader openBufferedReader(Charset charset) throws IOException {
            return Files.newBufferedReader(this.getFile(), charset);
        }

        public JsonElement toJson() throws IOException, JsonDeserializeException {
            try (BufferedReader bufferedReader = this.openBufferedReader()) {
                return JsonHelper.parse(bufferedReader);
            }
        }

        public <T> T deserialize(Class<T> cls) throws JsonDeserializeException, IOException {
            return Deserializer.decode(cls, toJson());
        }

        public void setLogger(Logger logger) {
            this.logger = logger;
        }

        @PublicAPI
        public byte[] getBytes(Charset charset) throws IOException {
            try (BufferedReader bufferedReader = this.openBufferedReader()) {
                return IOUtils.toByteArray(bufferedReader, charset);
            }
        }

        @PublicAPI
        public byte[] getBytes() throws IOException {
            try (InputStream inputStream = this.openStream()){
                return IOUtils.toByteArray(inputStream);
            }
        }
    }
}
