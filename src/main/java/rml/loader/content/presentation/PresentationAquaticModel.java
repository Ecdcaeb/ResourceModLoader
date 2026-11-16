package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Compact articulated geometry shared by the native 1.12 frog and nautilus equivalents. */
@SideOnly(Side.CLIENT)
final class PresentationAquaticModel extends ModelBase {
    private final ModelRenderer body;
    private final ModelRenderer head;
    private final ModelRenderer tail;
    private final ModelRenderer[] legs = new ModelRenderer[4];
    private final boolean nautilus;

    PresentationAquaticModel(boolean nautilus) {
        this.nautilus = nautilus;
        textureWidth = 64;
        textureHeight = 32;
        body = new ModelRenderer(this, 0, 0);
        body.setRotationPoint(0.0F, 17.0F, 0.0F);
        body.addBox(-4.0F, -3.0F, -6.0F, 8, 6, 12, 0.0F);
        head = new ModelRenderer(this, 0, 18);
        head.setRotationPoint(0.0F, 15.0F, -5.0F);
        head.addBox(-4.0F, -3.0F, -4.0F, 8, 5, 6, 0.0F);
        tail = new ModelRenderer(this, 36, 0);
        tail.setRotationPoint(0.0F, 17.0F, 5.0F);
        tail.addBox(-3.0F, -2.0F, 0.0F, 6, 4, 7, 0.0F);
        for (int i = 0; i < legs.length; i++) {
            legs[i] = new ModelRenderer(this, 36, 12 + i * 3);
            float x = (i & 1) == 0 ? -3.0F : 3.0F;
            float z = i < 2 ? -3.0F : 4.0F;
            legs[i].setRotationPoint(x, 18.0F, z);
            legs[i].addBox(x < 0 ? -2.0F : 0.0F, 0.0F, -1.0F, 2, nautilus ? 5 : 3, 4, 0.0F);
        }
        if (nautilus) {
            body.addBox(-5.0F, -4.0F, -6.0F, 10, 8, 12, 0.0F);
            tail.addBox(-4.0F, -3.0F, 0.0F, 8, 6, 8, 0.0F);
        }
    }

    @Override public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        body.render(scale);
        head.render(scale);
        tail.render(scale);
        for (ModelRenderer leg : legs) leg.render(scale);
    }

    @Override public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                            float netHeadYaw, float headPitch, float scale, Entity entity) {
        head.rotateAngleY = netHeadYaw * 0.017453292F;
        head.rotateAngleX = headPitch * 0.017453292F;
        tail.rotateAngleY = MathHelper.sin(ageInTicks * 0.12F) * 0.18F;
        float kick = MathHelper.cos(limbSwing * 0.6662F) * 0.65F * limbSwingAmount;
        legs[0].rotateAngleX = kick;
        legs[1].rotateAngleX = -kick;
        legs[2].rotateAngleX = -kick;
        legs[3].rotateAngleX = kick;
    }
}
