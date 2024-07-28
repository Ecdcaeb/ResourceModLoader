package rml.loader.api.mods;

import rml.jrx.announces.PublicAPI;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashMap;

@PublicAPI
public class ContainerHolder {
    public final ModContainer container;
    public final HashMap<ModuleType, Module> modules;
    public final int packVersion;
    public ContainerHolder(ModContainer container, Module[] modules){
        this(container, modules, 2);
    }

    public ContainerHolder(ModContainer container, Module[] modules, int packVersion){
        this.container = container;
        this.modules = new HashMap<>();
        for(Module module : modules){
            this.modules.put(module.moduleType, module);
        }
        this.packVersion = packVersion;
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
