package mods.Hileb.rml.compat.groovyscript;

import groovy.lang.GroovyClassLoader;
import net.minecraft.launchwrapper.Launch;
import org.codehaus.groovy.control.CompilationFailedException;

import javax.annotation.Nullable;
import java.util.HashMap;

public class GroovyByteClassLoader extends GroovyClassLoader {
    public static final GroovyByteClassLoader CLASS_LOADER = new GroovyByteClassLoader();
    protected final HashMap<String, Class<?>> loadedClasses = new HashMap<>();
    protected final HashMap<String, byte[]> cachedClasses = new HashMap<>();
    public GroovyByteClassLoader(){
        super(Launch.classLoader);
    }

    public void add(String className, byte[] groovy){
        cachedClasses.put(className, groovy);
    }

    @Nullable
    public byte[] getGroovy(String className){
        return cachedClasses.get(className);
    }

    @Nullable
    public Class<?> load(String name){
        byte[] bytes = getGroovy(name);
        if (bytes != null) return parseClass(new String(bytes));
        else return null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        final Class<?> result;
        if (this.loadedClasses.containsKey(name)) return loadedClasses.get(name);
        try {
            byte[] groovy = GroovyByteClassLoader.this.getGroovy(name);
            result = GroovyByteClassLoader.this.parseClass(new String(groovy));
        } catch (NullPointerException pae) {
            throw new ClassNotFoundException(name);
        }
        return result;
    }

    @Override
    public Class loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve) throws ClassNotFoundException, CompilationFailedException {
        Class<?> clazz = super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
        loadedClasses.put(name, clazz);
        return clazz;
    }
}
