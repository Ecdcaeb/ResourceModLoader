package rml.loader.content.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class StationBlock extends Block {
    public static final List<StationBlock> ALL = Collections.unmodifiableList(Arrays.asList(
            new StationBlock("blast_furnace"), new StationBlock("smoker"), new StationBlock("campfire"),
            new StationBlock("stonecutter"), new StationBlock("smithing_table")));

    private StationBlock(String name) {
        super(Material.ROCK);
        setRegistryName("rml", name);
        setTranslationKey("rml." + name);
        setHardness(3.5F);
        setResistance(12.0F);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.DECORATIONS);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new StationTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof StationTileEntity) player.displayGUIChest((StationTileEntity) world.getTileEntity(pos));
        return true;
    }
}