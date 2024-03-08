package mods.Hileb.rml.compat.groovyscript;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.sandbox.GroovySandbox;
import com.cleanroommc.groovyscript.sandbox.LoadStage;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import groovy.lang.Binding;
import groovy.lang.Script;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.event.early.FMLBeforeStageEvent;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/3 11:24
 **/
public enum GroovyScriptHandler {
    INSTANCE;
    @Subscribe
    @SuppressWarnings("unused")
    public void onStage(FMLBeforeStageEvent event){
        runInStage((LoaderState) event.stage);
    }
    public static final HashMap<ModContainer,GroovyRunConfig> REGISTRY = new HashMap<>();

    public static void load(){
        for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
            if ((containerHolder.opinion & ContainerHolder.Modules.MOD_GROOVY_SCRIPT) != 0){
                final ModContainer modContainer = containerHolder.container;
                GroovyRunConfig config = makeConfig(modContainer);
                if (config != null){
                    config.cacheClassesIntoClassLoader();
                    REGISTRY.put(modContainer, config);
                }
            }
        }
    }

    public static final HashMap<LoaderState,LoadStage> STAGES = new HashMap<>();
    static {
        STAGES.put(LoaderState.INITIALIZATION, LoadStage.PRE_INIT);
        STAGES.put(LoaderState.POSTINITIALIZATION, LoadStage.INIT);
        STAGES.put(LoaderState.AVAILABLE, LoadStage.POST_INIT);
    }

    public static void runInStage(LoaderState s){
        if (STAGES.containsKey(s)){
            LoadStage stage = STAGES.get(s);
            Binding binding = new Binding((Map<?,?>)ReflectionHelper.getPrivateValue(GroovySandbox.class, GroovyScript.getSandbox(),"bindings"));
            for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
                if ((containerHolder.opinion & ContainerHolder.Modules.MOD_GROOVY_SCRIPT) != 0){
                    final ModContainer modContainer = containerHolder.container;
                    if (REGISTRY.containsKey(modContainer)){
                        GroovyRunConfig config = REGISTRY.get(modContainer);
                        runGroovyInLoaderSpecially(modContainer, config, stage, binding);
                    }
                }
            }
        }
    }

    public static long runGroovyInLoaderSpecially(@Nonnull ModContainer container, @Nonnull GroovyRunConfig config, LoadStage loadStage, Binding binding) {
        // called via mixin between fml post init and load complete
        ModContainer current = Loader.instance().activeModContainer();
        Loader.instance().setActiveModContainer(container);

        long time = System.currentTimeMillis();

        HashSet<String> classes = config.loaders.get(loadStage.getName());
        if (classes != null){
            for(String clazzName : classes){
                Class<?> clazz = GroovyByteClassLoader.CLASS_LOADER.load(clazzName);
                //run script
                if (config.isScript(clazzName)){
                    Script script = GroovyByteClassLoader.CLASS_LOADER.makeScript(clazz, binding);
                    script.run();
                }
            }
        }

        time = System.currentTimeMillis() - time;
        RMLFMLLoadingPlugin.Container.LOGGER.info("Running Groovy scripts during {} took {} ms", loadStage.getName() , time);

        Loader.instance().setActiveModContainer(current);
        return time;
    }
    @Nullable
    public static GroovyRunConfig makeConfig(ModContainer container){
        AtomicReference<GroovyRunConfig> groovyRunConfig = new AtomicReference<>(null);
        try{
            FileHelper.findFile(container, "assets/" + container.getModId() + "/groovyscript/groovy.runconfig.json", path -> {
                try {
                    JsonObject jsonObject = FileHelper.GSON.fromJson(FileHelper.getCachedFile(path), JsonObject.class);
                    groovyRunConfig.set(new GroovyRunConfig(container, jsonObject));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }catch (InvalidPathException invalidPathException){
            return null;
        }
        return groovyRunConfig.get();
    }
}
