package rml.loader.content.presentation;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/** Hanging painting with authored dimensions and a pack-owned texture. */
public final class EntityPresentationPainting extends EntityHanging implements IEntityAdditionalSpawnData {
    private int presentationWidth = 16;
    private int presentationHeight = 16;
    private String presentationVariant = "";

    public EntityPresentationPainting(World world) { super(world); }
    public EntityPresentationPainting(World world, BlockPos position, EnumFacing facing) {
        super(world, position);
        updateFacingWithBoundingBox(facing);
    }

    public void configure(ResourceLocation variant, int width, int height) {
        presentationVariant = variant.toString();
        presentationWidth = width;
        presentationHeight = height;
        updateFacingWithBoundingBox(facingDirection);
    }

    public ResourceLocation getPresentationVariant() {
        return presentationVariant.isEmpty() ? null : new ResourceLocation(presentationVariant);
    }

    @Override public int getWidthPixels() { return presentationWidth; }
    @Override public int getHeightPixels() { return presentationHeight; }

    @Override public void onBroken(Entity breaker) {
        if (world.isRemote) return;
        if (world.getGameRules().getBoolean("doEntityDrops")) entityDropItem(new ItemStack(Items.PAINTING), 0.0F);
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_PAINTING_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }

    @Override public void playPlaceSound() {
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_PAINTING_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("RMLVariant", presentationVariant);
        tag.setInteger("RMLWidth", presentationWidth);
        tag.setInteger("RMLHeight", presentationHeight);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        presentationVariant = tag.getString("RMLVariant");
        presentationWidth = Math.max(1, Math.min(256, tag.getInteger("RMLWidth")));
        presentationHeight = Math.max(1, Math.min(256, tag.getInteger("RMLHeight")));
        if (!presentationVariant.isEmpty()) updateFacingWithBoundingBox(facingDirection);
    }

    @Override public void writeSpawnData(ByteBuf buffer) {
        PacketBuffer packet = new PacketBuffer(buffer);
        if (presentationVariant.length() > 256) throw new IllegalArgumentException("Painting variant ID exceeds 256 characters");
        packet.writeString(presentationVariant);
        packet.writeVarInt(presentationWidth);
        packet.writeVarInt(presentationHeight);
    }

    @Override public void readSpawnData(ByteBuf buffer) {
        PacketBuffer packet = new PacketBuffer(buffer);
        presentationVariant = packet.readString(256);
        presentationWidth = Math.max(1, Math.min(256, packet.readVarInt()));
        presentationHeight = Math.max(1, Math.min(256, packet.readVarInt()));
        updateFacingWithBoundingBox(facingDirection);
    }
}
