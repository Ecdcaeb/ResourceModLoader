package mods.rml.api.mods.module.plugin;

import mods.rml.api.announces.PublicAPI;
import mods.rml.api.mods.module.IModuleType;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:55
 **/

@PublicAPI
public interface ICustomModulePlugin {
    /**
     * @return the module type, the proxy core of a real ModuleType
     */
    IModuleType[] createModuleTypes();

    /**
     * @return the name
     */
    String getPluginName();
}
