package mods.Hileb.rml.api.mods;

import net.minecraftforge.fml.common.ModContainer;

import java.util.HashMap;
import java.util.HashSet;

public class ContainerHolder {
    public final ModContainer container;
    @Deprecated
    public final int opinion;
    public final HashSet<Modules> modules;
    public ContainerHolder(ModContainer container, Modules[] modules){
        this.container = container;
        this.opinion = opinion;
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
        CUSTOM_VILLAGERS;
    }
}
