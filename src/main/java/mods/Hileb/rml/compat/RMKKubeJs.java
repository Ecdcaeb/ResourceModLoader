package mods.Hileb.rml.compat;

import dev.latvian.kubejs.KubeJS;
import dev.latvian.kubejs.script.BindingsEvent;
import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptManager;
import dev.latvian.kubejs.script.ScriptPack;
import mods.Hileb.rml.RMLModContainer;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.core.RMLFMLPlugin;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            if (!packs.containsKey(modContainer.getModId())) {
                try {
                    packs.put(modContainer.getModId(),(ScriptPack)newPack.invoke(ScriptManager.instance,modContainer.getModId()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            CraftingHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/kubejs",null,
                    (root, file) ->
                    {
                        Loader.instance().setActiveModContainer(modContainer);

                        load(ScriptManager.instance, file,modContainer.getModId());

                        Loader.instance().setActiveModContainer(RMLFMLPlugin.Container.INSTANCE);
                        return true;
                    },true, true);


        }
    }
    private static void load(ScriptManager manager, Path zipPath, String modid) {
        String name = zipPath.toUri().getPath();
        int weight;
        if (name!=null && name.endsWith(".js")) {
            weight = 0;
            if (name.equals("init.js")) {
                weight = -100;
            }
            ScriptFile scriptFile = new ScriptFile(packs.get(modid), name, weight, () -> new InputStreamReader(Files.newInputStream(zipPath), StandardCharsets.UTF_8));
            manager.scripts.put(scriptFile.path, scriptFile);
            KubeJS.LOGGER.info("Found script at " + scriptFile.path);
        }
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


ResourceModLoader

}



