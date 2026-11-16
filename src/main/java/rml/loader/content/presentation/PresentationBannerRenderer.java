package rml.loader.content.presentation;

import net.minecraft.block.BlockBanner;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityBannerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
final class PresentationBannerRenderer extends TileEntityBannerRenderer {
    @Override public void render(TileEntityBanner banner, double x, double y, double z,
                                 float partialTicks, int destroyStage, float alpha) {
        super.render(banner, x, y, z, partialTicks, destroyStage, alpha);
        NBTTagList patterns = banner.getTileData().getTagList("RMLPatterns", 8);
        if (patterns.tagCount() == 0) return;
        IBlockState state = banner.getWorld().getBlockState(banner.getPos());
        float rotation = state.getProperties().containsKey(BlockBanner.ROTATION)
                ? state.getValue(BlockBanner.ROTATION) * 22.5F
                : state.getValue(BlockBanner.FACING).getHorizontalAngle();
        for (int i = 0; i < patterns.tagCount(); i++) {
            PresentationCodecs.SurfaceDefinition surface = PresentationClient.surface("banner_pattern", new ResourceLocation(patterns.getStringTagAt(i)));
            if (surface != null) drawLayer(surface, x, y, z, rotation);
        }
    }

    private void drawLayer(PresentationCodecs.SurfaceDefinition surface, double x, double y, double z, float rotation) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 1.45D, z + 0.5D);
        GlStateManager.rotate(180.0F - rotation, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(((surface.color >> 16) & 255) / 255.0F, ((surface.color >> 8) & 255) / 255.0F, (surface.color & 255) / 255.0F, 1.0F);
        bindTexture(surface.texture);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-0.625D, -1.25D, -0.015D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(0.625D, -1.25D, -0.015D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(0.625D, 1.25D, -0.015D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(-0.625D, 1.25D, -0.015D).tex(0.0D, 0.0D).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
