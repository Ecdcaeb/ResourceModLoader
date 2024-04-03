package mods.rml.api.mods;

import mods.rml.api.PublicAPI;
import net.minecraftforge.fml.common.ModContainer;

import java.util.Arrays;
import java.util.HashSet;

@PublicAPI
public class ContainerHolder {
    public final ModContainer container;
    public final HashSet<Modules> modules;
    public ContainerHolder(ModContainer container, Modules[] modules){
        this.container = container;
        this.modules = new HashSet<>(Arrays.asList(modules));
    }

    public ModContainer getContainer() {
        return container;
    }

    public HashSet<Modules> getModules() {
        return modules;
    }

    public ContainerHolder(ModContainer container){
        this(container, Modules.values());
    }

    public enum Modules {
        CONFIG_OVERRIDE,
        CONFIG_REDEFAULT,
        MOD_CRT,
        FUNCTIONS,
        MOD_KUBEJS,
        LOOT_TABLES,
        ORE_DIC,
        REGISTRY_REMAP,
        MOD_GROOVY_SCRIPT,
        CUSTOM_VILLAGERS,
        SPLASH_TEXT,
        MOD_TREASURES;
    }
}
