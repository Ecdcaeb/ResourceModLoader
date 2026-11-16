package rml.loader.content.presentation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/** Applies an authored texture layer to a placed native banner. */
final class PresentationBannerPatternItem extends Item {
    private final net.minecraft.util.ResourceLocation pattern;

    PresentationBannerPatternItem(net.minecraft.util.ResourceLocation pattern) {
        this.pattern = pattern;
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.DECORATIONS);
    }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                               EnumHand hand, net.minecraft.util.EnumFacing facing,
                                               float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityBanner)) return EnumActionResult.FAIL;
        if (!world.isRemote) {
            NBTTagCompound data = tile.getTileData();
            NBTTagList patterns = data.getTagList("RMLPatterns", Constants.NBT.TAG_STRING);
            if (patterns.tagCount() >= 8) return EnumActionResult.FAIL;
            patterns.appendTag(new NBTTagString(pattern.toString()));
            data.setTag("RMLPatterns", patterns);
            tile.markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            if (!player.capabilities.isCreativeMode) player.getHeldItem(hand).shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }

}
