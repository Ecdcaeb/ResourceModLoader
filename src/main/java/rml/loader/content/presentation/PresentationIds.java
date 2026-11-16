package rml.loader.content.presentation;

import net.minecraft.util.ResourceLocation;

/** Type-qualified catalog key; vanilla registries may reuse the same identifier in different families. */
final class PresentationIds {
    private PresentationIds() { }
    static ResourceLocation key(String type, ResourceLocation id) {
        return new ResourceLocation(id.getNamespace(), "__presentation/" + type + "/" + id.getPath());
    }
}
