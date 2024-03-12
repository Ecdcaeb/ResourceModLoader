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
public class GroovyRunConfig {
    public final HashMap<String,HashSet<String>> loaders = new HashMap<>();
    protected ModContainer container;
    protected HashMap<String,String> class_to_path = new HashMap<>();
    protected HashSet<String> scripts = new HashSet<>();

    public GroovyRunConfig(ModContainer container, JsonObject jsonObject){
        this.container = container;
        JsonObject loaders = jsonObject.getAsJsonObject("loaders");
        for(Map.Entry<String,JsonElement> entry:loaders.entrySet()){
            HashSet<String> hashSet = new HashSet<>();
            hashSet.addAll(Arrays.asList(JsonHelper.getAsArray(entry.getValue(), JsonHelper::getString)));
            this.loaders.put(entry.getKey(), hashSet);
        }
        for(Map.Entry<String, JsonElement> entry:jsonObject.getAsJsonObject("classes").entrySet()){
            class_to_path.put(entry.getKey(), entry.getValue().getAsString());
        }
        scripts.addAll(Arrays.asList(JsonHelper.getAsArray(jsonObject.get("scripts"), JsonHelper::getString)));
    }

    public boolean isScript(String name){
        return scripts.contains(name);
    }

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
