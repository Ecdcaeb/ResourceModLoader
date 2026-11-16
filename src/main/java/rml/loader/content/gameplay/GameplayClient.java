package rml.loader.content.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

@SideOnly(Side.CLIENT)
public final class GameplayClient {
    private GameplayClient() { }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (java.util.Map.Entry<Item, ResourceLocation> entry : NativeDataItem.MODELS.entrySet()) {
            ModelLoader.setCustomModelResourceLocation(entry.getKey(), 0,
                    new ModelResourceLocation(entry.getValue(), "inventory"));
        }
        for (java.util.Map.Entry<Block, ResourceLocation> entry : NativeDataBlock.MODELS.entrySet()) {
            Block block = entry.getKey();
            ResourceLocation model = entry.getValue();
            ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return new ModelResourceLocation(model, "normal");
                }
            });
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                int variants = block instanceof NativeDataBlock ? ((NativeDataBlock) block).variantCount() : 1;
                ModelResourceLocation inventory = new ModelResourceLocation(model, "inventory");
                for (int metadata = 0; metadata < variants; metadata++) ModelLoader.setCustomModelResourceLocation(item, metadata, inventory);
            }
        }
    }
}