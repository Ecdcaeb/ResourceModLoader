package rml.loader.api.mods.module;

import net.minecraft.util.ResourceLocation;
import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PublicAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:49
 **/

@EarlyClass
@PublicAPI
public interface IModuleType {
    /**
     * @return the name of enum
     */
    ResourceLocation getName();

    /**
     * @return the default location
     */
    String getDefaultLocation();

    /**
     * @return is it a single file, not a walking directory.
     */
    boolean isFile();
}
