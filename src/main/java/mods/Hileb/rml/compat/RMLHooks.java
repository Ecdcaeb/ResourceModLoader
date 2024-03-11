package mods.Hileb.rml.compat;

import net.minecraft.launchwrapper.Launch;

import java.io.File;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/11 23:00
 **/
public class RMLHooks {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/3/11 22:57
     **/
    public static class GroovyHook {
        public static File initScriptPath(){
            return Boolean.parseBoolean(System.getProperty("groovyscript.use_examples_folder")) ?
                    new File(Launch.minecraftHome.getParentFile(), "examples") :
                    new File(Launch.minecraftHome, "groovy");
        }
    }
}
