package rml.layer.compat.kubejs;

import com.google.common.io.ByteSource;
import dev.latvian.kubejs.KubeJS;
import dev.latvian.kubejs.script.BindingsEvent;
import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptManager;
import dev.latvian.kubejs.script.ScriptPack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.reflection.jvm.MethodAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.core.RMLFMLLoadingPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 11:33
 **/
@PrivateAPI
public class RMKKubeJs {
    @PrivateAPI
    @SubscribeEvent
    public static void onJSLoad(BindingsEvent event){
        RMLFMLLoadingPlugin.Container.LOGGER.info("Inject KubeJS");
        ResourceModLoader.loadContainerHolders(ModuleType.valueOf(new ResourceLocation("rml", "mod_kubejs")), containerHolder -> {
            if (!packs.containsKey(containerHolder.getContainer().getModId())) {
                packs.put(containerHolder.getContainer().getModId(), newPack.invoke(ScriptManager.instance, containerHolder.getContainer().getModId()));
            }
        });
        ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "mod_kubejs")), (context) -> {
            if (context.isExtension("js")) {
                try {
                    char[] fileBytes = ByteSource.wrap(context.getBytes()).asCharSource(StandardCharsets.UTF_8).read().toCharArray();
                    load(ScriptManager.instance, context.getFile().toUri().toString(), fileBytes, context.getContainerHolder().getContainer().getModId());
                } catch (IOException e) {
                    context.error(e, "Error at Loading kubejs file for {}", context.getFile());
                }
            }}, RMKKubeJs.class);
    }
    @PrivateAPI
    private static void load(ScriptManager manager, String name, char[] file, String modid) {
        KubeJS.LOGGER.debug("Found script at {}", name);
        int weight;
        weight = 0;
        if (name.endsWith("/init.js")) {
            weight = -100;
        }
        ScriptFile scriptFile = new BuffedJSFile(packs.get(modid), name, weight, file);
        manager.scripts.put(name, scriptFile);
        KubeJS.LOGGER.debug("Load script at {}", name);
    }
    @PrivateAPI public static final MethodAccessor<ScriptPack, ScriptManager> newPack = ReflectionHelper.getMethodAccessor(ScriptManager.class, "newPack", null, String.class);
    @PrivateAPI public static final Map<String, ScriptPack> packs;

    static {
        packs = ReflectionHelper.getPrivateValue(ScriptManager.class,ScriptManager.instance,"packs");
    }

}



