package mods.Hileb.rml.compat.groovyscript;

import com.cleanroommc.groovyscript.sandbox.LoadStage;
import com.google.gson.JsonObject;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.codehaus.groovy.runtime.InvokerHelper;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static com.cleanroommc.groovyscript.GroovyScript.getSandbox;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/3 11:24
 **/
public class GroovyScriptContainer {
    public static long runGroovyScriptsInLoaderSpecially(@Nonnull ModContainer container, @Nonnull GroovyRunConfig config, LoadStage loadStage) {
        // called via mixin between fml post init and load complete
        ModContainer current = Loader.instance().activeModContainer();
        Loader.instance().setActiveModContainer(container);

        long time = System.currentTimeMillis();

        HashSet<String> classes = config.loaders.get(loadStage.getName());
        if (classes != null){
            for(String clazzName : classes){
                Class<?> clazz = GroovyByteClassLoader.CLASS_LOADER.load(clazzName);
                if (clazz != null){
                    Script script = InvokerHelper.createScript(clazz, binding);
                    if (false) {
                        setCurrentScript(scriptFile.toString());
                        script.run();
                        setCurrentScript(null);
                    }
                }
            }
        }


        time = System.currentTimeMillis() - time;
        RMLFMLLoadingPlugin.Container.LOGGER.info("Running Groovy scripts during {} took {} ms", loadStage.getName(), time);

        Loader.instance().setActiveModContainer(current);
        return time;
    }
    @Nullable
    public static GroovyRunConfig makeConfig(ModContainer container){
        AtomicReference<GroovyRunConfig> groovyRunConfig = new AtomicReference<>(null);
        try{
            FileHelper.findFile(container, "assets/" + container.getModId() + "/groovyscript/groovyscript.runconfig.json", path -> {
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
