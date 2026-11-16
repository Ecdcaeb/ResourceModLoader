package rml.loader.content;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.storage.loot.LootTable;
import rml.loader.ResourceModLoader;

import java.util.Map;

/** Stable native call boundary. Providers remain constructor-injected and compiler-independent. */
public final class CompiledContentHooks {
    private CompiledContentHooks() { }

    public static LootTable findLootTable(ResourceLocation id) {
        return ResourceModLoader.runtime().getCompiledLoot().findLootTable(id);
    }

    public static Template findStructure(ResourceLocation id) {
        return ResourceModLoader.runtime().getCompiledStructures().findTemplate(id);
    }

    public static void loadAdvancements(AdvancementList list, Map<ResourceLocation, Advancement.Builder> builders) {
        ResourceModLoader.runtime().getCompiledAdvancements().augment(builders);
        list.loadAdvancements(builders);
    }
}
