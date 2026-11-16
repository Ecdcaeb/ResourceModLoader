package rml.loader.content;

import com.google.gson.JsonElement;
import net.minecraft.util.ResourceLocation;
import rml.runtime.OwnedRegistry;
import rml.runtime.PreparedUnit;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Ordered compiled groups. Consumers query explicit registry identities, not ore-dictionary aliases. */
public final class CompiledTagModule {
    private final Map<String, Map<ResourceLocation, Set<ResourceLocation>>> values = new HashMap<>();
    private final Map<String, OwnedRegistry<ResourceLocation, Set<ResourceLocation>>> registries = new HashMap<>();

    public PreparedUnit prepare(CompiledResource resource) {
        String registry = "function_tag".equals(resource.getType()) ? "function" : resource.getType().substring(4);
        Set<ResourceLocation> entries = new LinkedHashSet<>();
        try {
            for (JsonElement entry : resource.getValue().getAsJsonArray()) entries.add(new ResourceLocation(entry.getAsString()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid compiled tag at " + resource.getSource(), e);
        }
        Set<ResourceLocation> snapshot = Collections.unmodifiableSet(entries);
        return scope -> scope.own(registry(registry).register(resource.getId(), snapshot));
    }

    public boolean contains(String registry, ResourceLocation tag, ResourceLocation entry) {
        Map<ResourceLocation, Set<ResourceLocation>> tags = values.get(registry);
        Set<ResourceLocation> entries = tags == null ? null : tags.get(tag);
        if (entries == null) throw new IllegalArgumentException("Unknown " + registry + " tag: " + tag);
        return entries.contains(entry);
    }

    private OwnedRegistry<ResourceLocation, Set<ResourceLocation>> registry(String registry) {
        OwnedRegistry<ResourceLocation, Set<ResourceLocation>> owner = registries.get(registry);
        if (owner == null) {
            Map<ResourceLocation, Set<ResourceLocation>> tags = new HashMap<>();
            owner = new OwnedRegistry<>(tags);
            values.put(registry, tags);
            registries.put(registry, owner);
        }
        return owner;
    }
}
