package rml.loader.content.presentation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Renders authored transparent trim masks over worn armor meshes. */
@SideOnly(Side.CLIENT)
final class PresentationTrimLayer implements LayerRenderer<EntityLivingBase> {
    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
    private static final ModelBiped[] MODELS = {new ModelBiped(1.01F), new ModelBiped(1.01F), new ModelBiped(0.51F), new ModelBiped(1.01F)};
    @Override public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                        float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        for (int slotIndex = 0; slotIndex < ARMOR_SLOTS.length; slotIndex++) {
            EntityEquipmentSlot slot = ARMOR_SLOTS[slotIndex];
            ItemStack armor = entity.getItemStackFromSlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof ItemArmor)) continue;
            NBTTagCompound tag = armor.getTagCompound();
            if (tag == null || !tag.hasKey("rml_trim", 10)) continue;
            NBTTagCompound trim = tag.getCompoundTag("rml_trim");
            String materialName = trim.getString("material"), patternName = trim.getString("pattern");
            if (materialName.isEmpty() || patternName.isEmpty()) continue;
            ResourceLocation materialId = new ResourceLocation(materialName);
            ResourceLocation patternId = new ResourceLocation(patternName);
            ResourceLocation trimTexture = PresentationClient.trimTexture(materialId, patternId, slot == EntityEquipmentSlot.LEGS);
            if (trimTexture == null) continue;
            ModelBiped model = MODELS[slotIndex];
            model.setVisible(false);
            model.isSneak = entity.isSneaking();
            model.isRiding = entity.isRiding();
            model.isChild = entity.isChild();
            switch (slot) {
                case HEAD: model.bipedHead.showModel = model.bipedHeadwear.showModel = true; break;
                case CHEST: model.bipedBody.showModel = model.bipedRightArm.showModel = model.bipedLeftArm.showModel = true; break;
                case LEGS: model.bipedBody.showModel = model.bipedRightLeg.showModel = model.bipedLeftLeg.showModel = true; break;
                case FEET: model.bipedRightLeg.showModel = model.bipedLeftLeg.showModel = true; break;
                default: break;
            }
            model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
            GlStateManager.enableBlend();
            boolean decal = PresentationClient.trimDecal(patternId);
            if (decal) { GlStateManager.enablePolygonOffset(); GlStateManager.doPolygonOffset(-1.0F, -1.0F); }
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            PresentationClient.bindSurface(trimTexture);
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            if (decal) GlStateManager.disablePolygonOffset();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
        }
    }

    @Override public boolean shouldCombineTextures() { return false; }
}
