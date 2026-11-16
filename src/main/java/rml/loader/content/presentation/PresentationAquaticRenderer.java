package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/** Renderer for the species added because this 1.12.2 client has no native counterpart. */
@SideOnly(Side.CLIENT)
final class PresentationAquaticRenderer<T extends EntityLivingBase> extends RenderLivingBase<T> {
    PresentationAquaticRenderer(RenderManager manager, boolean nautilus) {
        super(manager, new PresentationAquaticModel(nautilus), nautilus ? 0.55F : 0.35F);
    }

    @Override protected ResourceLocation getEntityTexture(T entity) {
        return PresentationClient.entityTexture(entity);
    }
}
