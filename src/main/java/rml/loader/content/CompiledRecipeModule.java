package rml.loader.content;

import com.google.gson.JsonElement;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Startup recipes. Native factories may perform irreversible work while decoding. */
public final class CompiledRecipeModule {
    private final ContentCatalog catalog;

    public CompiledRecipeModule(ContentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void register(IForgeRegistry<IRecipe> registry) {
        Objects.requireNonNull(registry, "registry");
        Map<ResourceLocation, CompiledResource> resources = catalog.snapshot().resources("recipe");
        Map<ResourceLocation, IRecipe> recipes = new LinkedHashMap<>();
        for (CompiledResource resource : resources.values()) {
            try {
                JsonElement value = resource.getValue();
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("Recipe value must be an object");
                }
                JsonContext context = new JsonContext(resource.getId().getNamespace());
                if (!CraftingHelper.processConditions(value.getAsJsonObject(), "conditions", context)) {
                    continue;
                }
                IRecipe recipe = CraftingHelper.getRecipe(value.getAsJsonObject(), context);
                if (recipe == null) {
                    throw new IllegalArgumentException("Recipe factory returned null");
                }
                recipe.setRegistryName(resource.getId());
                if (registry.containsKey(resource.getId()) && !(registry instanceof ForgeRegistry)) {
                    throw new IllegalArgumentException("Recipe overrides require a ForgeRegistry");
                }
                recipes.put(resource.getId(), recipe);
            } catch (RuntimeException failure) {
                throw failure(resource, failure);
            }
        }
        // Decode every definition before mutating the crafting registry. This cannot
        // roll back side effects performed inside native smelt/brew/anvil factories.
        for (Map.Entry<ResourceLocation, IRecipe> entry : recipes.entrySet()) {
            try {
                if (registry.containsKey(entry.getKey())) {
                    ((ForgeRegistry<IRecipe>) registry).remove(entry.getKey());
                }
                registry.register(entry.getValue());
            } catch (RuntimeException failure) {
                throw failure(resources.get(entry.getKey()), failure);
            }
        }
    }

    private static IllegalArgumentException failure(CompiledResource resource, RuntimeException cause) {
        return new IllegalArgumentException(resource.getSource() + ": recipe " + resource.getId()
                + ": " + cause.getMessage(), cause);
    }
}
