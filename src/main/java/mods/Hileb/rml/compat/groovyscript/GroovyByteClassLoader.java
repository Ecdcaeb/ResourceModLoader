package mods.Hileb.rml.compat.groovyscript;

import groovy.lang.GroovyClassLoader;
import net.minecraft.launchwrapper.Launch;

import javax.annotation.Nullable;
import java.util.HashMap;

public class GroovyByteClassLoader extends GroovyClassLoader {
    public static final GroovyByteClassLoader CLASS_LOADER = new GroovyByteClassLoader();
    protected HashMap<String, byte[]> loadedClasses = new HashMap<>();
    public GroovyByteClassLoader(){
        super(Launch.classLoader);
    }

    public void add(String className, byte[] groovy){
        loadedClasses.put(className, groovy);
    }

    @Nullable
    public byte[] getGroovy(String className){
        return loadedClasses.get(className);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        final Class<?> result;
        try {
            byte[] groovy = GroovyByteClassLoader.this.getGroovy(name);
            result = GroovyByteClassLoader.this.parseClass(new String(groovy));
        } catch (NullPointerException pae) {
            throw new ClassNotFoundException(name);
        }
        return result;
    }
}
