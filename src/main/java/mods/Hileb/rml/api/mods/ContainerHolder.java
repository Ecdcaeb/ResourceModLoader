package mods.Hileb.rml.api.mods;

import net.minecraftforge.fml.common.ModContainer;

import java.util.HashMap;
import java.util.HashSet;

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
        public static final HashMap<String,Integer> MAP = new HashMap<>();
        public static final int CONFIG_OVERRIDE = 1;
        public static final int CONFIG_REDEFAULT = 2;
        public static final int MOD_CRT = 4;
        public static final int FUNCTIONS = 8;
        public static final int MOD_KUBEJS = 16;
        public static final int LOOT_TABLES = 32;
        public static final int ORE_DIC = 64;
        public static final int REGISTRY_REMAP = 128;
        public static final int MOD_GROOVY_SCRIPT = 256;

        static {
            MAP.put("CONFIG_OVERRIDE", CONFIG_OVERRIDE);
            MAP.put("CONFIG_REDEFAULT", CONFIG_REDEFAULT);
            MAP.put("MOD_CRT", MOD_CRT);
            MAP.put("FUNCTIONS", FUNCTIONS);
            MAP.put("MOD_KUBEJS", MOD_KUBEJS);
            MAP.put("LOOT_TABLES", LOOT_TABLES);
            MAP.put("ORE_DIC", ORE_DIC);
            MAP.put("REGISTRY_REMAP", REGISTRY_REMAP);
            MAP.put("MOD_GROOVY_SCRIPT", MOD_GROOVY_SCRIPT);
        }

        public static boolean is(int opinion, int select){
            return (opinion & select) != 0;
        }

        public static int fromNames(String[] names){
            int opinion = 0;
            for(String name : names){
                opinion = opinion | MAP.get(name);
            }
            return opinion;
        }

        public static final int ALL = CONFIG_OVERRIDE | CONFIG_REDEFAULT | MOD_CRT | FUNCTIONS | MOD_KUBEJS | LOOT_TABLES | ORE_DIC | REGISTRY_REMAP | MOD_GROOVY_SCRIPT;
    }
}
