package rml.loader.module;

import com.google.gson.JsonParseException;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.villagers.IVillager;
import rml.loader.runtime.ModuleLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Decodes village declarations; the native registry event decides when to apply them. */
public final class VillagerModule {
    private final ModuleLoader modules;

    public VillagerModule(ModuleLoader modules) { this.modules = modules; }

    public List<IVillager> read() {
        List<IVillager> villagers = new ArrayList<>();
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "custom_villagers")), context -> {
            if (!context.isExtension("json")) return;
            try {
                villagers.add(context.deserialize(IVillager.class));
            } catch (IOException | JsonParseException | JsonDeserializeException e) {
                context.error(e, "Cannot load villager {}", context.getResourceLocation());
            }
        }, VillagerModule.class);
        return Collections.unmodifiableList(villagers);
    }
}
