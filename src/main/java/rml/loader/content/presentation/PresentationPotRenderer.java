package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

/** Tessellated ceramic silhouette with the authored pot pattern on every side. */
@SideOnly(Side.CLIENT)
final class PresentationPotRenderer extends TileEntitySpecialRenderer<TileEntityPresentationPot> {
    private static final float[] Y = {0.06F, 0.20F, 0.64F, 0.78F, 0.88F};
    private static final float[] R = {0.22F, 0.28F, 0.34F, 0.25F, 0.38F};

    @Override public void render(TileEntityPresentationPot tile, double x, double y, double z,
                                 float partialTicks, int destroyStage, float alpha) {
        ResourceLocation texture = PresentationClient.surfaceTexture("decorated_pot_pattern", tile.getPattern());
        if (texture == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(texture);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        for (int i = 0; i < Y.length - 1; i++) {
            float y0 = Y[i], y1 = Y[i + 1], r0 = R[i], r1 = R[i + 1];
            float v0 = (float) i / (Y.length - 1), v1 = (float) (i + 1) / (Y.length - 1);
            side(buffer, -r0, y0, r0, y0, -r1, y1, r1, y1, v0, v1, 0);
            side(buffer, r0, y0, -r0, y0, r1, y1, -r1, y1, v0, v1, 1);
            sideX(buffer, -r0, -r1, y0, y1, v0, v1, 0);
            sideX(buffer, r0, r1, y0, y1, v0, v1, 1);
        }
        float radius = R[R.length - 1];
        float top = Y[Y.length - 1];
        vertex(buffer, -radius, top, -0.24F, 0, 0); vertex(buffer, radius, top, -0.24F, 1, 0);
        vertex(buffer, radius, top, 0.24F, 1, 1); vertex(buffer, -radius, top, 0.24F, 0, 1);
        Tessellator.getInstance().draw();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void side(BufferBuilder b, float x0, float y0, float x1, float y1,
                             float x2, float y2, float x3, float y3, float v0, float v1, int reverse) {
        float z = reverse == 0 ? -0.24F : 0.24F;
        vertex(b, x0, y0, z, 0, v1); vertex(b, x1, y1, z, 1, v1);
        vertex(b, x3, y3, z, 1, v0); vertex(b, x2, y2, z, 0, v0);
    }

    private static void sideX(BufferBuilder b, float x0, float x1, float y0, float y1,
                              float v0, float v1, int reverse) {
        float z0 = reverse == 0 ? -0.24F : 0.24F;
        float z1 = -z0;
        vertex(b, x0, y0, z0, 0, v1); vertex(b, x0, y0, z1, 1, v1);
        vertex(b, x1, y1, z1, 1, v0); vertex(b, x1, y1, z0, 0, v0);
    }

    private static void vertex(BufferBuilder b, float x, float y, float z, float u, float v) {
        b.pos(x, y, z).tex(u, v).endVertex();
    }
}
