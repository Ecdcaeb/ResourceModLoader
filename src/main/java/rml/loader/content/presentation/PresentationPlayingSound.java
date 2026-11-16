package rml.loader.content.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Explicit radius attenuation and native sound-engine lifetime. */
@SideOnly(Side.CLIENT)
final class PresentationPlayingSound extends MovingSound {
    private final PresentationPlayback.Play source;
    private final net.minecraft.world.World world;
    private final BlockPos block;
    private int remaining, age;
    PresentationPlayingSound(PresentationPlayback.Play source) {
        super(new SoundEvent(source.sound), SoundCategory.RECORDS);
        this.source = source; this.world = Minecraft.getMinecraft().world;
        this.block = new BlockPos(source.x, source.y, source.z); this.remaining = source.duration;
        this.xPosF = (float) source.x; this.yPosF = (float) source.y; this.zPosF = (float) source.z;
        this.pitch = source.pitch; this.attenuationType = ISound.AttenuationType.NONE;
        this.repeat = false;
        attenuate();
    }
    @Override public void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != world || mc.player == null || --remaining <= 0) { donePlaying = true; return; }
        ++age;
        if (source.entity >= 0) {
            Entity entity = world.getEntityByID(source.entity);
            if (!(entity instanceof EntityLivingBase) || entity.isDead) { donePlaying = true; return; }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (age > 5 && (!living.isHandActive() || !(living.getActiveItemStack().getItem() instanceof PresentationInstrumentItem))) { donePlaying = true; return; }
            xPosF = (float) entity.posX; yPosF = (float) entity.posY; zPosF = (float) entity.posZ;
        } else if (source.jukebox) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(block);
            if (!(state.getBlock() instanceof BlockPresentationJukebox) || !state.getValue(net.minecraft.block.BlockJukebox.HAS_RECORD)) { donePlaying = true; return; }
        }
        attenuate();
    }
    private void attenuate() {
        net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
        volume = player == null ? 0 : source.volume * (float) Math.max(0, 1 - player.getDistance(xPosF, yPosF, zPosF) / source.range);
    }
}
