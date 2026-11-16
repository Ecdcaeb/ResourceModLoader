package rml.loader.content;

import com.google.gson.JsonElement;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementManager;
import net.minecraft.util.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Adds compiled builders immediately before the native advancement graph is loaded. */
public final class CompiledAdvancementModule {
    private final ContentCatalog catalog;

    public CompiledAdvancementModule(ContentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void augment(Map<ResourceLocation, Advancement.Builder> builders) {
        Objects.requireNonNull(builders, "builders");
        Map<ResourceLocation, Advancement.Builder> decoded = new LinkedHashMap<>();
        for (CompiledResource resource : catalog.snapshot().resources("advancement").values()) {
            try {
                JsonElement value = resource.getValue();
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("Advancement value must be an object");
                }
                Advancement.Builder builder = AdvancementManager.GSON.fromJson(value, Advancement.Builder.class);
                if (builder == null) {
                    throw new IllegalArgumentException("Advancement decoder returned null");
                }
                decoded.put(resource.getId(), builder);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException(resource.getSource() + ": advancement " + resource.getId()
                        + ": " + failure.getMessage(), failure);
            }
        }
        // Parent resolution remains with AdvancementList, including references to
        // ordinary native advancements and compiled definitions in other packs.
        builders.putAll(decoded);
    }
}
