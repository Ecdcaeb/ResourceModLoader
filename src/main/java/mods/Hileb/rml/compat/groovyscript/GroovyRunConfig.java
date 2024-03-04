package mods.Hileb.rml.compat.groovyscript;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.api.file.JsonHelper;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/4 12:39
 **/
public abstract class GroovyRunConfig {
    public GroovyRunConfig(ModContainer container, JsonObject jsonObject){
        this.container = container;
        JsonObject loaders = jsonObject.getAsJsonObject("loaders");
        preInitClasses.addAll(Arrays.asList(JsonHelper.getAsArray(loaders.get("preInit"), JsonHelper::getString)));
        onInitClasses.addAll(Arrays.asList(JsonHelper.getAsArray(loaders.get("init"), JsonHelper::getString)));
        postInitClasses.addAll(Arrays.asList(JsonHelper.getAsArray(loaders.get("postInit"), JsonHelper::getString)));
        for(Map.Entry<String, JsonElement> entry:jsonObject.getAsJsonObject("classes").entrySet()){
            class_to_path.put(entry.getKey(), entry.getValue().getAsString());
        }
    }
    public HashSet<String> preInitClasses = new HashSet<>();
    public HashSet<String> onInitClasses = new HashSet<>();
    public HashSet<String> postInitClasses = new HashSet<>();
    protected ModContainer container;
    protected HashMap<String,String> class_to_path = new HashMap<>();
    public void cacheClassesIntoClassLoader(){
        File source = container.getSource();
        if (source != null){
            if (source.isFile()){
                FileSystem fileSystem = null;
                try {
                    fileSystem = FileSystems.newFileSystem(source.toPath(), null);
                    Path path;
                    for(Map.Entry<String,String> entry : class_to_path.entrySet()){
                        path = fileSystem.getPath("/" + entry.getValue());
                        try {
                            GroovyByteClassLoader.CLASS_LOADER.add(entry.getKey(), FileHelper.getByteSource(path).read());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }finally {
                    IOUtils.closeQuietly(fileSystem);
                }
            }
            else if (source.isDirectory()){
                Path root = source.toPath();
                Path path;
                for(Map.Entry<String,String> entry : class_to_path.entrySet()){
                    path = root.resolve(entry.getValue());
                    try {
                        GroovyByteClassLoader.CLASS_LOADER.add(entry.getKey(), FileHelper.getByteSource(path).read());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
