package mods.rml.api.mods.module.plugin;

import mods.rml.api.mods.module.IModuleType;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 10:55
 **/
public interface ICustomModulePlugin {
    IModuleType[] createModuleTypes();
    String getPluginName();
}
