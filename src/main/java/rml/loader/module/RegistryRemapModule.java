package rml.loader.module;

import net.minecraft.util.ResourceLocation;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.registry.remap.RemapCollection;
import rml.loader.runtime.ModuleLoader;

import java.io.IOException;

/** Reads missing-registry remap declarations before native missing-mapping handling. */
public final class RegistryRemapModule {
    private final ModuleLoader modules;

    public RegistryRemapModule(ModuleLoader modules) { this.modules = modules; }

    public void load() {
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "registry_remap")), context -> {
            if (!context.isExtension("json")) return;
            try {
                for (RemapCollection remap : context.deserialize(RemapCollection[].class)) {
                    RemapCollection.Manager.merge(remap);
                }
            } catch (IOException | JsonDeserializeException e) {
                context.error(e, "Cannot load registry remaps {}", context.getResourceLocation());
            }
        }, RegistryRemapModule.class);
    }
}
