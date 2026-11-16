package rml.loader.module;

import net.minecraft.util.ResourceLocation;
import rml.loader.api.event.LootTableRegistryEvent;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.runtime.ModuleLoader;

/** Registers loot resource identities during the native registry event. */
public final class LootTableModule {
    private final ModuleLoader modules;

    public LootTableModule(ModuleLoader modules) { this.modules = modules; }

    public void load(LootTableRegistryEvent event) {
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "loot_tables")), context -> {
            if (!context.isExtension("json")) return;
            try {
                event.register(context.getResourceLocation());
            } catch (RuntimeException e) {
                context.error(e, "Cannot register loot table {}", context.getResourceLocation());
            }
        }, LootTableModule.class);
    }
}
