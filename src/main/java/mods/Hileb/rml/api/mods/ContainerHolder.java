package mods.Hileb.rml.api.mods;

import net.minecraftforge.fml.common.ModContainer;

public class ContainerHolder {
    public final ModContainer container;
    public final int opinion;
    public ContainerHolder(ModContainer container, int opinion){
        this.container = container;
        this.opinion = opinion;
    }

    public ContainerHolder(ModContainer container){
        this(container, Opinion.ALL);
    }

    public static class Opinion{
        public static final int CONFIG_OVERRIDE = 1;
        public static final int CONFIG_REDEFAULT = 2;
        public static final int MOD_CRT = 4;
        public static final int FUNCTIONS = 8;
        public static final int MOD_KUBEJS = 16;
        public static final int LOOT_TABLES = 32;
        public static final int ORE_DIC = 64;
        public static final int REGISTRY_REMAP = 128;
        public static final int MOD_GROOVY_SCRIPT = 256;

        public static boolean is(int opinion, int select){
            return (opinion & select) != 0;
        }

        public static final int ALL = CONFIG_OVERRIDE | CONFIG_REDEFAULT | MOD_CRT | FUNCTIONS | MOD_KUBEJS | LOOT_TABLES | ORE_DIC | REGISTRY_REMAP | MOD_GROOVY_SCRIPT;
    }
}
