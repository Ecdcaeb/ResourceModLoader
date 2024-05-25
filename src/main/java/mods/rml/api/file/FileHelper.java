package mods.rml.api.file;

import com.google.common.io.ByteSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mods.rml.api.announces.PublicAPI;
import mods.rml.api.mods.ContainerHolder;
import mods.rml.api.mods.module.Module;
import mods.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.IOUtils;

import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:23
 **/
@PublicAPI
public class FileHelper {
    @PublicAPI
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void findAssets(ContainerHolder containerHolder, Module module, ModFileConsumer consumer){
        if (module.moduleType.isFile){
            findFile(containerHolder, "assets/" + containerHolder.getContainer().getModId() + "/" + module.location, consumer);
        }else {
            findAssets(containerHolder, module.location, consumer);
        }
    }

    public static void findAssets(ContainerHolder containerHolder, String base, ModFileConsumer consumer){
        findFiles(containerHolder, "assets/" + containerHolder.getContainer().getModId() + "/" + base, consumer);
    }

    public static void findFiles(ContainerHolder containerHolder, String base, ModFileConsumer consumer)
    {
        final ModContainer mod = containerHolder.getContainer();

        if ("minecraft".equals(mod.getModId()))
        {
            return;
        }
        File source = mod.getSource();

        FileSystem fs = null;

        try
        {
            Path root = null;

            if (source.isFile())
            {
                try
                {
                    fs = FileSystems.newFileSystem(source.toPath(), null);
                    root = fs.getPath("/" + base);
                }
                catch (IOException e)
                {
                    RMLFMLLoadingPlugin.LOGGER.error("Error loading FileSystem from jar: ", e);
                    return;
                }
            }
            else if (source.isDirectory())
            {
                root = source.toPath().resolve(base);
            }

            if (root == null || !Files.exists(root))
                return;


            if (consumer != null)
            {
                Iterator<Path> itr;
                try
                {
                    itr = Files.walk(root).iterator();
                }
                catch (IOException e)
                {
                    RMLFMLLoadingPlugin.LOGGER.error("Error iterating filesystem for: {}", mod.getModId(), e);
                    return;
                }

                while (itr.hasNext())
                {
                    consumer.accept(containerHolder, root, itr.next());
                }
            }
        }
        finally
        {
            IOUtils.closeQuietly(fs);
        }
    }

    @PublicAPI
    public static void findFile(ContainerHolder containerHolder, String base, ModFileConsumer processor) throws InvalidPathException
    {
        ModContainer mod = containerHolder.container;
        File source = mod.getSource();
        if ("minecraft".equals(mod.getModId()))
        {
            return;
        }
        if (source.isFile())
        {
            try
            {
                FileSystem fs = FileSystems.newFileSystem(source.toPath(), null);
                Path path;
                try{
                    path = fs.getPath("/" + base);
                }catch (InvalidPathException e){
                    return;
                }
                processor.accept(containerHolder, path, path);
                IOUtils.closeQuietly(fs);
            }
            catch (IOException e) {
                RMLFMLLoadingPlugin.LOGGER.error("Error loading FileSystem from jar: ", e);
            }
        }
        else if (source.isDirectory())
        {
            Path path;
            try{
                path = source.toPath().resolve(base);
            } catch (InvalidPathException e){
                return;
            }
            processor.accept(containerHolder, path, path);
        }
    }
    @PublicAPI
    public static CharArrayReader getCachedFile(Path path) throws IOException {
        return new CharArrayReader(IOUtils.toCharArray(Files.newBufferedReader(path)));
    }
    @PublicAPI
    public static ByteSource getByteSource(Path path, Charset charset) throws IOException {
        return ByteSource.wrap(IOUtils.toByteArray(Files.newBufferedReader(path), charset));
    }

    @PublicAPI
    public static ByteSource getByteSource(Path path) throws IOException {
        return ByteSource.wrap(IOUtils.toByteArray(Files.newBufferedReader(path), StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    public interface ModFileConsumer{
        void accept(ContainerHolder containerHolder, Path root, Path file);
    }
}
