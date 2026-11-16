package rml.loader.content.presentation;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

/** Authored decorated-pot block whose tile renderer uses the selected surface texture. */
public final class BlockPresentationPot extends BlockContainer {
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(0.12D, 0.0D, 0.12D, 0.88D, 0.9D, 0.88D);
    private final ResourceLocation defaultPattern;

    public BlockPresentationPot(ResourceLocation defaultPattern) {
        super(Material.ROCK);
        this.defaultPattern = defaultPattern;
        setHardness(0.7F);
        setResistance(2.0F);
        setRegistryName("rml", "decorated_pot");
        setTranslationKey("rml.decorated_pot");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.DECORATIONS);
    }

    @Override public TileEntity createNewTileEntity(World world, int metadata) {
        TileEntityPresentationPot tile = new TileEntityPresentationPot();
        tile.setPattern(defaultPattern);
        return tile;
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) { return BOUNDS; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }

    @Override public boolean isPassable(net.minecraft.world.IBlockAccess world, BlockPos pos) { return false; }
}
