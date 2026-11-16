package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws full authored painting images rather than cropping the legacy 1.12 painting atlas. */
@SideOnly(Side.CLIENT)
final class PresentationPaintingRenderer extends Render<EntityPresentationPainting> {
    PresentationPaintingRenderer(RenderManager manager) { super(manager); }

    @Override public void doRender(EntityPresentationPainting painting, double x, double y, double z, float entityYaw, float partialTicks) {
        PresentationClient.VariantState state = PresentationClient.getVariant(painting.getEntityId());
        ResourceLocation variant = state == null ? painting.getPresentationVariant() : state.id;
        PresentationCodecs.PaintingVariant paintingVariant = PresentationClient.painting(variant);
        if (paintingVariant == null) return;
        ResourceLocation texture = paintingVariant.texture;
        float width = paintingVariant.width;
        float height = paintingVariant.height;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        bindTexture(texture);
        float left = -width * 0.5F, right = width * 0.5F;
        float bottom = -height * 0.5F, top = height * 0.5F;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(left, bottom, -0.01D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(right, bottom, -0.01D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(right, top, -0.01D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(left, top, -0.01D).tex(0.0D, 0.0D).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override protected ResourceLocation getEntityTexture(EntityPresentationPainting entity) {
        PresentationClient.VariantState state = PresentationClient.getVariant(entity.getEntityId());
        PresentationCodecs.PaintingVariant variant = PresentationClient.painting(state == null ? entity.getPresentationVariant() : state.id);
        return variant == null ? null : variant.texture;
    }
}
