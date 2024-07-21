package mods.rml.api.mods.module;

import mods.rml.api.announces.EarlyClass;
import mods.rml.api.announces.PublicAPI;

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
    String getName();

    /**
     * @return the default location
     */
    String getDefaultLocation();

    /**
     * @return is it a single file, not a walking directory.
     */
    boolean isFile();

    /**
     * @param moduleType please catch your type when it generated.
     */
    void consume(ModuleType moduleType);
}
