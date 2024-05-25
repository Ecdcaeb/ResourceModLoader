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
    String getName();
    String getDefaultLocation();

    boolean isFile();
    void consume(ModuleType moduleType);
}
