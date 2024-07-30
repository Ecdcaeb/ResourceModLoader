package rml.layer.compat.groovyscripts;

import net.minecraft.util.ResourceLocation;
import rml.loader.api.mods.module.ModuleType;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/30 10:26
 **/
public class RMLGsLoader {
    public static class Module{
        public static ModuleType TYPE = new ModuleType(new ResourceLocation("rml", "MOD_GROOVY_SCRIPT"), "groovy_script", false);
    }
}
