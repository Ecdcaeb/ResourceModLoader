package rml.loader.content.presentation;

import net.minecraft.item.ItemRecord;
import net.minecraft.util.SoundEvent;

/** Public construction bridge for data-pack registered jukebox records. */
final class PresentationRecordItem extends ItemRecord {
    final PresentationCodecs.SoundPlayback playback;
    private final BlockPresentationJukebox jukebox;
    PresentationRecordItem(String name, SoundEvent sound, PresentationCodecs.SoundPlayback playback, BlockPresentationJukebox jukebox) {
        super(name, sound); this.playback = playback; this.jukebox = jukebox;
    }
    @Override public net.minecraft.util.EnumActionResult onItemUse(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing side, float hitX, float hitY, float hitZ) {
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.block.BlockJukebox) || state.getValue(net.minecraft.block.BlockJukebox.HAS_RECORD)) return net.minecraft.util.EnumActionResult.PASS;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return net.minecraft.util.EnumActionResult.FAIL;
        if (!world.isRemote) {
            if (state.getBlock() != jukebox && !world.setBlockState(pos, jukebox.getDefaultState(), 3)) return net.minecraft.util.EnumActionResult.FAIL;
            net.minecraft.item.ItemStack stack = player.getHeldItem(hand);
            jukebox.insertRecord(world, pos, world.getBlockState(pos), stack);
            world.updateComparatorOutputLevel(pos, jukebox);
            stack.shrink(1);
        }
        return net.minecraft.util.EnumActionResult.SUCCESS;
    }
}
