package mods.Hileb.rml.compat;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.File;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/11 23:00
 **/
public class RMLHooks {

    public static void redefineResourceLocation(ResourceLocation resourceLocation){
        if ("contenttweak".equals(resourceLocation.getResourceDomain()) && resourceLocation.getResourcePath().contains(":")){
            String[] paths = resourceLocation.getResourcePath().split(":");
            if (paths.length == 2) {
                ObfuscationReflectionHelper.setPrivateValue(ResourceLocation.class, resourceLocation, paths[0], "field_110626_a");
                ObfuscationReflectionHelper.setPrivateValue(ResourceLocation.class, resourceLocation, paths[1], "field_110625_b");
            }
        }
    }

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
