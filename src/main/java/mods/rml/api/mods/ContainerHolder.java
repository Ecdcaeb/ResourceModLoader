package mods.rml.api.mods;

import rml.jrx.announces.PublicAPI;
import mods.rml.api.mods.module.Module;
import mods.rml.api.mods.module.ModuleType;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashMap;

@PublicAPI
public class ContainerHolder {
    public final ModContainer container;
    public final HashMap<ModuleType, Module> modules;
    public ContainerHolder(ModContainer container, Module[] modules){
        this.container = container;
        this.modules = new HashMap<>();
        for(Module module : modules){
            this.modules.put(module.moduleType, module);
        }
    }

    public ModContainer getContainer() {
        return container;
    }

    public HashMap<ModuleType,Module> getModules() {
        return modules;
    }

    public boolean hasModule(ModuleType module){
        return modules.containsKey(module);
    }

    public ContainerHolder(ModContainer container){
        this(container, ModuleType.getAllForDefault());
    }

    @FunctionalInterface
    public interface ModuleConsumer{
        void accept(ModuleType module, ContainerHolder containerHolder);
    }
}
