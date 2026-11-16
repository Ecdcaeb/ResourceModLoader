package rml.loader.content.gameplay;

import net.minecraft.item.ItemBlock;

/** Metadata is the persisted native variant both when placing and when collecting the block. */
final class NativeBlockItem extends ItemBlock {
    private final int variants;
    NativeBlockItem(NativeDataBlock block) {
        super(block);
        variants = block.variantCount();
        setHasSubtypes(variants > 1);
    }
    @Override public int getMetadata(int damage) { return Math.max(0, Math.min(variants - 1, damage)); }
}
