package rml.loader.content.presentation;

import net.minecraft.block.BlockJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Requires a registered data-defined record and a real server world with an idle player. */
public final class PresentationPlaybackContractTest {
    private PresentationPlaybackContractTest() { }

    public static void verifyInWorld(WorldServer world, EntityPlayerMP player, BlockPos pos, ItemStack record) {
        check(player.world == world && !player.isHandActive(), "Playback proof requires an idle player in the supplied world");
        check(world.isAirBlock(pos), "Playback proof requires an empty block position");
        check(record.getCount() == 1 && record.getItem() instanceof PresentationRecordItem, "Playback proof requires one registered authored record");
        PresentationCodecs.SoundPlayback playback = ((PresentationRecordItem) record.getItem()).playback;
        check(playback.duration >= 2 && playback.comparator > 0, "Playback proof requires a positive signal and at least two ticks");
        ItemStack held = player.getHeldItemMainhand();
        try {
            world.setBlockState(pos, Blocks.JUKEBOX.getDefaultState(), 3);
            ItemStack inserted = record.copy();
            player.setHeldItem(EnumHand.MAIN_HAND, inserted);
            EnumActionResult interaction = player.interactionManager.processRightClickBlock(player, world, inserted,
                    EnumHand.MAIN_HAND, pos, EnumFacing.UP, .5F, .5F, .5F);
            check(interaction == EnumActionResult.SUCCESS, "Native record insertion must succeed");
            IBlockState state = world.getBlockState(pos);
            check(state.getBlock() instanceof BlockPresentationJukebox && state.getValue(BlockJukebox.HAS_RECORD), "Insertion must set the native occupied jukebox state");
            TileEntity tile = world.getTileEntity(pos);
            check(tile instanceof BlockPresentationJukebox.PlayingRecord, "Insertion must retain the playback tile");
            BlockPresentationJukebox.PlayingRecord playing = (BlockPresentationJukebox.PlayingRecord) tile;
            check(ItemStack.areItemStacksEqual(record, playing.getRecord()), "Changing HAS_RECORD must retain the inserted record");
            check(state.getBlock().getComparatorInputOverride(state, world, pos) == playback.comparator, "Changing HAS_RECORD must retain the authored playback signal");
            int elapsed = playback.duration / 2;
            for (int tick = 0; tick < elapsed; tick++) playing.update();
            NBTTagCompound saved = playing.writeToNBT(new NBTTagCompound());
            world.removeTileEntity(pos);
            TileEntity restored = TileEntity.create(world, saved);
            check(restored instanceof BlockPresentationJukebox.PlayingRecord, "Native tile NBT must restore the registered playback type");
            world.setTileEntity(pos, restored);
            playing = (BlockPresentationJukebox.PlayingRecord) restored;
            check(ItemStack.areItemStacksEqual(record, playing.getRecord()), "Native tile NBT must preserve the inserted record");
            for (int tick = elapsed; tick < playback.duration; tick++) {
                check(state.getBlock().getComparatorInputOverride(state, world, pos) == playback.comparator, "Restored playback must retain its signal until its remaining duration expires");
                playing.update();
            }
            check(state.getBlock().getComparatorInputOverride(state, world, pos) == 0, "Restored playback must stop at the original duration");
            check(ItemStack.areItemStacksEqual(record, playing.getRecord()), "Playback expiry must leave the record available for ejection");
            System.out.println("PresentationPlaybackContractTest live native proof passed");
        } finally {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof BlockJukebox.TileEntityJukebox) ((BlockJukebox.TileEntityJukebox) tile).setRecord(ItemStack.EMPTY);
            world.setBlockToAir(pos);
            player.setHeldItem(EnumHand.MAIN_HAND, held);
            player.inventoryContainer.detectAndSendChanges();
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
