package mods.Hileb.rml.compat.groovyscript;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.sandbox.GroovySandbox;
import com.cleanroommc.groovyscript.sandbox.RunConfig;
import com.google.gson.JsonObject;
import groovy.lang.GroovyClassLoader;
import mods.Hileb.rml.api.file.FileHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/3 11:24
 **/
public class GroovyScriptContainer {
    public static final GroovyClassLoader classLoader = new GroovyClassLoader(Launch.classLoader);

    public static Class<?> defineClass(byte[] groovyScript){
        return classLoader.parseClass(new String(groovyScript));
    }
    public static JsonObject updateRunConfigJson(ModContainer container, JsonObject json) {
        json.addProperty("packName", container.getName());
        json.addProperty("packId", container.getModId());
        json.addProperty("version", container.getVersion());
        return json;
    }


    /**
     * {@link GroovySandbox#getClassFiles()}
     * {@link GroovySandbox#getScriptFiles()}
     * {@link GroovyScript#getResourcesFile()}
     *
     * hard to generate a running time {@link File}
     * all logic writed with {@link File}
     * **/
    public static Class<?>[] loadScriptsToClass(ModContainer container, String[] paths){
        LinkedList<Class<?>> list = new LinkedList<>();
        for(String path : paths){
            FileHelper.findFiles(container, "assets/" + container.getModId() + "/config/groovyscript/" + (path.substring(0, path.length() - 1),null,
                    (root, file) ->
                    {
                        try {
                            list.add(defineClass(FileHelper.getByteSource(file).read()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    },false, false);
        }
    }

    public static RunConfig runConfig(ModContainer container){
        final RunConfig[] config = new RunConfig[1];
        FileHelper.findFile(container, "assets/" + container.getModId() + "/config/groovyscript/runConfig.json", path -> {
            try {
                config[0] = new RunConfig(updateRunConfigJson(container, FileHelper.GSON.fromJson(FileHelper.getCachedFile(path), JsonObject.class)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return config[0];
    }
}
