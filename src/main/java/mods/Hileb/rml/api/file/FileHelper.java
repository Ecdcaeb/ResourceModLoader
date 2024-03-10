package mods.Hileb.rml.api.file;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mods.Hileb.rml.api.PublicAPI;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.FMLLog;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:23
 **/
@PublicAPI
public class FileHelper {
    @PublicAPI
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @PublicAPI
    public static boolean findFiles(ModContainer mod, String base, Function<Path, Boolean> preprocessor, BiFunction<Path, Path, Boolean> processor, boolean defaultUnfoundRoot, boolean visitAllFiles)
    {
        FileSystem fs = null;
        try
        {
            File source = mod.getSource();

            if ("minecraft".equals(mod.getModId()))
            {
                return true;

            }

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
                    FMLLog.log.error("Error loading FileSystem from jar: ", e);
                    return false;
                }
            }
            else if (source.isDirectory())
            {
                root = source.toPath().resolve(base);
            }

            if (root == null || !Files.exists(root))
                return defaultUnfoundRoot;

            if (preprocessor != null)
            {
                Boolean cont = preprocessor.apply(root);
                if (cont == null || !cont.booleanValue())
                    return false;
            }

            boolean success = true;

            if (processor != null)
            {
                Iterator<Path> itr = null;
                try
                {
                    itr = Files.walk(root).iterator();
                }
                catch (IOException e)
                {
                    FMLLog.log.error("Error iterating filesystem for: {}", mod.getModId(), e);
                    return false;
                }

                while (itr != null && itr.hasNext())
                {
                    Boolean cont = processor.apply(root, itr.next());

                    if (visitAllFiles)
                    {
                        success &= cont != null && cont;
                    }
                    else if (cont == null || !cont)
                    {
                        return false;
                    }
                }
            }

            return success;
        }
        finally
        {
            IOUtils.closeQuietly(fs);
        }
    }

    @PublicAPI
    public static void findFile(ModContainer mod, String base, Consumer<Path> processor) throws InvalidPathException
    {
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
                    processor.accept(fs.getPath("/" + base));
                    IOUtils.closeQuietly(fs);
                }
                catch (IOException e)
                {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("Error loading FileSystem from jar: ", e);
                }
            }
            else if (source.isDirectory())
            {
                processor.accept(source.toPath().resolve(base));
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
}
