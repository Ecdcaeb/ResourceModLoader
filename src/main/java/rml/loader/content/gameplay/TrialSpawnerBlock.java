package rml.loader.content.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class TrialSpawnerBlock extends Block {
    public static final TrialSpawnerBlock INSTANCE = new TrialSpawnerBlock();

    private TrialSpawnerBlock() {
        super(Material.ROCK);
        setRegistryName("rml", "trial_spawner");
        setTranslationKey("rml.trial_spawner");
        setHardness(3.5F);
        setResistance(12.0F);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.DECORATIONS);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TrialSpawnerTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TrialSpawnerTileEntity) {
            ((TrialSpawnerTileEntity) world.getTileEntity(pos)).activate(player);
        }
        return true;
    }
}