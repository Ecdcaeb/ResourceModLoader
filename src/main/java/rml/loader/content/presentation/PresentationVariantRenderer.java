package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/** Keeps the native model geometry while drawing the selected authored entity texture. */
@SideOnly(Side.CLIENT)
final class PresentationVariantRenderer extends RenderLivingBase<EntityLivingBase> {
    PresentationVariantRenderer(RenderManager manager, ModelBase model) {
        super(manager, model, 0.5F);
    }

    @Override protected ResourceLocation getEntityTexture(EntityLivingBase entity) {
        return PresentationClient.entityTexture(entity);
    }
}
