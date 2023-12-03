package mods.Hileb.rml.compat.kubejs;

import dev.latvian.kubejs.KubeJS;
import dev.latvian.kubejs.script.BindingsEvent;
import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptManager;
import dev.latvian.kubejs.script.ScriptPack;
import mods.Hileb.rml.RMLModContainer;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.core.RMLFMLPlugin;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 11:33
 **/
public class RMKKubeJs {
    @SubscribeEvent
    public static void onJSLoad(BindingsEvent event){
        RMLFMLPlugin.Container.LOGGER.info("Inject KubeJS");
        for(RMLModContainer modContainer: ResourceModLoader.containers){
            Loader.instance().setActiveModContainer(modContainer);
            if (!packs.containsKey(modContainer.getModId())) {
                try {
                    packs.put(modContainer.getModId(),(ScriptPack)newPack.invoke(ScriptManager.instance,modContainer.getModId()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            File source = modContainer.getSource();
            FileSystem fs;
            Path root = null;
            String base="assets/" + modContainer.getModId() + "/kubejs";
            if (source.isFile()){
                try
                {
                    fs = FileSystems.newFileSystem(source.toPath(), null);
                    root = fs.getPath("/" + base);
                }
                catch (IOException e)
                {
                    RMLFMLPlugin.Container.LOGGER.error("Error loading FileSystem from jar: ", e);
                    continue;
                }
            }else if (source.isDirectory())
            {
                root = source.toPath().resolve(base);
            }
            Iterator<Path> itr;
            try
            {
                itr = Files.walk(root).iterator();
            }
            catch (IOException e)
            {
                RMLFMLPlugin.Container.LOGGER.error("Error iterating filesystem for: {}",modContainer.getModId(), e);
                continue;
            }
            while (itr.hasNext())
            {
                Path path= itr.next();
                if (path.toUri().toString().endsWith(".js")){
                    load(ScriptManager.instance,path,modContainer.getModId());
                }
            }
            Loader.instance().setActiveModContainer(RMLFMLPlugin.Container.INSTANCE);
        }
    }
    private static void load(ScriptManager manager, Path zipPath, String modid) {
        String name = zipPath.toUri().toString();
        KubeJS.LOGGER.info("Found script at " + name);
        int weight;
        weight = 0;
        if (name.endsWith("/init.js")) {
            weight = -100;
        }
        ScriptFile scriptFile = new ScriptFile(packs.get(modid), name, weight, () -> new BufferedReader(Files.newBufferedReader(zipPath)));
        manager.scripts.put(scriptFile.path, scriptFile);
        KubeJS.LOGGER.info("Load script at " + scriptFile.path);
    }
    public static final Method newPack;
    public static final Map<String, ScriptPack> packs;
    static {
        try {
            newPack=ScriptManager.class.getDeclaredMethod("newPack", String.class);
            newPack.setAccessible(true);
            packs= ReflectionHelper.getPrivateValue(ScriptManager.class,ScriptManager.instance,"packs");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}



