package mods.Hileb.rml.compat.kubejs;

import dev.latvian.kubejs.KubeJS;
import dev.latvian.kubejs.script.BindingsEvent;
import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptManager;
import dev.latvian.kubejs.script.ScriptPack;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 11:33
 **/
public class RMKKubeJs {
    @SubscribeEvent
    public static void onJSLoad(BindingsEvent event){
        RMLFMLLoadingPlugin.Container.LOGGER.info("Inject KubeJS");
        for(ModContainer modContainer: Loader.instance().getActiveModList()){
            Loader.instance().setActiveModContainer(modContainer);
            if (!packs.containsKey(modContainer.getModId())) {
                try {
                    packs.put(modContainer.getModId(),(ScriptPack)newPack.invoke(ScriptManager.instance,modContainer.getModId()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/kubejs",null,
                    (root, file) ->
                    {
                        String relative = root.relativize(file).toString();
                        if (!"js".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;
                        BufferedReader bufferedReader=null;
                        try {
                            char[] fileBytes;
                            bufferedReader=Files.newBufferedReader(file);
                            fileBytes=IOUtils.toCharArray(bufferedReader);
                            load(ScriptManager.instance, file.toUri().toString(),fileBytes,modContainer.getModId());

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }finally {
                            IOUtils.closeQuietly(bufferedReader);
                        }

                        return true;
                    },true, true);
            
            Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
        }
    }
    private static void load(ScriptManager manager, String name,char[] file, String modid) {
        KubeJS.LOGGER.info("Found script at " + name);
        int weight;
        weight = 0;
        if (name.endsWith("/init.js")) {
            weight = -100;
        }
        ScriptFile scriptFile = new BuffedJSFile(packs.get(modid), name, weight, file);
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



