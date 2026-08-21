package rml.layer.compat.groovyscripts;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.sandbox.CustomGroovyScriptEngine;
import com.cleanroommc.groovyscript.sandbox.GroovyScriptClassLoader;
import com.cleanroommc.groovyscript.sandbox.GroovyScriptSandbox;
import com.cleanroommc.groovyscript.sandbox.LoadStage;
import groovy.lang.Binding;
import groovy.lang.Script;
import net.minecraft.util.ResourceLocation;
import org.codehaus.groovy.runtime.InvokerHelper;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.annotations.RewriteWhenCleanroom;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.reflection.jvm.FieldAccessor;
import rml.loader.api.reflection.jvm.MethodAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.api.utils.file.FileHelper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/30 16:49
 **/
@PrivateAPI
@RewriteWhenCleanroom
public class RMLGroovySandBox {
    public static final MethodAccessor<Void, GroovyScriptSandbox> m_runScript = ReflectionHelper.getMethodAccessor(GroovyScriptSandbox.class, "runScript", "runScript", Script.class);
    public static final MethodAccessor<Void, GroovyScriptSandbox> m_runClass = ReflectionHelper.getMethodAccessor(GroovyScriptSandbox.class, "runClass", "runClass", Class.class);
    public static final FieldAccessor<LoadStage, GroovyScriptSandbox> f_currentLoadStage = ReflectionHelper.getFieldAccessor(GroovyScriptSandbox.class, "currentLoadStage");
    public static final FieldAccessor<Long, GroovyScriptSandbox> f_compileTime = ReflectionHelper.getFieldAccessor(GroovyScriptSandbox.class, "compileTime");
    public static final FieldAccessor<Long, GroovyScriptSandbox> f_runTime = ReflectionHelper.getFieldAccessor(GroovyScriptSandbox.class, "runTime");
    private static Method parseClassRaw;

    private static Method parseClassRaw(GroovyScriptClassLoader classLoader) {
        if (parseClassRaw == null) {
            try {
                parseClassRaw = classLoader.getClass().getDeclaredMethod("parseClassRaw", String.class, String.class);
                parseClassRaw.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("GroovyScript 1.4 CustomGroovyScriptEngine$ScriptClassLoader.parseClassRaw(String, String) is missing", e);
            }
        }
        return parseClassRaw;
    }

    public static void load(GroovyScriptSandbox sandbox, Binding binding, Set<String> executedClasses, boolean run){
        String loader = f_currentLoadStage.get(sandbox).getName();
        HashSet<NamedScript> files = new HashSet<>();
        for(Map.Entry<ContainerHolder, RMLGrsLoader.RunConfig> entry : RMLGrsLoader.MOD.entrySet()){
            for(String classPath : entry.getValue().getClasses(loader)){
                ResourceLocation name = new ResourceLocation(entry.getKey().getContainer().getModId(), classPath);
                byte[] file = FileHelper.findFile(entry.getKey().getContainer(), classPath);
                if (file == null) {
                    GroovyLog.get().errorMC("RML Groovy script '{}' was not found in pack '{}'", classPath, entry.getKey().getContainer().getModId());
                    continue;
                }
                if (Preprocessor.validatePreprocessor(name, file)) {
                    files.add(new NamedScript(name, file));
                }
            }
        }

        if (files.isEmpty()) {
            return;
        }

        CustomGroovyScriptEngine engine = sandbox.getEngine();
        GroovyScriptClassLoader groovyClassLoader = engine.getClassLoader();
        String scriptRoot = GroovyScript.getScriptPath().replace('\\', '/') + "/";

        for(NamedScript file : files){
            String scriptName = makeFakeRelativePath(file.getName(), scriptRoot);
            if (executedClasses.contains(scriptName)) {
                continue;
            }
            long t = System.currentTimeMillis();
            Class<?> clazz = compile(scriptName, file, groovyClassLoader);
            f_compileTime.set(sandbox, f_compileTime.get(sandbox) + (System.currentTimeMillis() - t));
            if (clazz == null) {
                GroovyLog.get().errorMC("Error loading RML Groovy script {}", file.getName());
                continue;
            }
            executedClasses.add(scriptName);
            if (!run) {
                continue;
            }
            t = System.currentTimeMillis();
            if (clazz.getSuperclass() != Script.class) {
                m_runClass.invoke(sandbox, clazz);
            } else {
                Script script = InvokerHelper.createScript(clazz, binding);
                m_runScript.invoke(sandbox, script);
            }
            f_runTime.set(sandbox, f_runTime.get(sandbox) + (System.currentTimeMillis() - t));
        }
    }

    public static String makeFakeRelativePath(ResourceLocation resourceLocation, String root){
        return root + resourceLocation.getNamespace() + "/" + resourceLocation.getPath();
    }

    public static Class<?> compile(String name, NamedScript namedScript, GroovyScriptClassLoader classLoader){
        try {
            String source = new String(namedScript.getFile(), StandardCharsets.UTF_8);
            return (Class<?>) parseClassRaw(classLoader).invoke(classLoader, source, name);
        } catch (Throwable e) {
            GroovyLog.get().exception("An error occurred while trying to compile RML Groovy script " + namedScript.getName(), e);
            return null;
        }
    }
}
