package rml.loader.content.presentation;

import net.minecraft.block.BlockJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Native jukebox behavior with a persisted playback clock and authored comparator strength. */
final class BlockPresentationJukebox extends BlockJukebox {
    BlockPresentationJukebox() {
        setRegistryName(new ResourceLocation("rml", "presentation_jukebox"));
        setTranslationKey("jukebox"); setHardness(2.0F); setResistance(10.0F);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing side, float hitX, float hitY, float hitZ) {
        boolean handled = super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
        if (handled && !world.isRemote) world.setBlockState(pos, net.minecraft.init.Blocks.JUKEBOX.getDefaultState(), 3);
        return handled;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PlayingRecord(); }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof PlayingRecord)) return 0;
        PlayingRecord record = (PlayingRecord) tile;
        if (record.remaining <= 0 || record.getRecord().isEmpty()) return 0;
        return record.getRecord().getItem() instanceof PresentationRecordItem
                ? ((PresentationRecordItem) record.getRecord().getItem()).playback.comparator : super.getComparatorInputOverride(state, world, pos);
    }
    @Override public Item getItemDropped(IBlockState state, java.util.Random random, int fortune) { return Item.getItemFromBlock(net.minecraft.init.Blocks.JUKEBOX); }
    @Override public ItemStack getItem(World world, BlockPos pos, IBlockState state) { return new ItemStack(net.minecraft.init.Blocks.JUKEBOX); }

    public static final class PlayingRecord extends BlockJukebox.TileEntityJukebox implements ITickable {
        private int remaining;
        private boolean resume;
        @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
            return oldState.getBlock() != newState.getBlock();
        }
        @Override public void setRecord(ItemStack stack) {
            super.setRecord(stack);
            remaining = !stack.isEmpty() && stack.getItem() instanceof PresentationRecordItem ? ((PresentationRecordItem) stack.getItem()).playback.duration : 0;
            resume = remaining > 0;
            markDirty();
        }
        @Override public void update() {
            if (world.isRemote || remaining <= 0) return;
            if (getRecord().isEmpty() || !(getRecord().getItem() instanceof PresentationRecordItem)) { remaining = 0; markDirty(); return; }
            if (resume) {
                PresentationCodecs.SoundPlayback value = ((PresentationRecordItem) getRecord().getItem()).playback;
                PresentationPlayback.play(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new PresentationCodecs.SoundPlayback(value.sound, value.volume, value.pitch, remaining, value.range, value.comparator), -1, true);
                resume = false;
            }
            --remaining; markDirty();
            if (remaining == 0) world.updateComparatorOutputLevel(pos, getBlockType());
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setInteger("RMLRemaining", remaining); return tag; }
        @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); remaining = Math.max(0, tag.getInteger("RMLRemaining")); resume = remaining > 0; }
    }
}
