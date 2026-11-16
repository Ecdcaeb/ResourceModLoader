package rml.loader.content.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeDataBlock extends Block {
    public static final PropertyInteger VARIANT = PropertyInteger.create("variant", 0, 15);
    static final Map<Block, ResourceLocation> MODELS = new LinkedHashMap<>();
    private final NativeBlockDefinition definition;

    NativeDataBlock(NativeBlockDefinition definition) {
        super(definition.material);
        this.definition = definition;
        setRegistryName(definition.id);
        setTranslationKey(definition.id.getNamespace() + "." + definition.id.getPath());
        setHardness(definition.hardness);
        setResistance(definition.resistance);
        setLightLevel(definition.light);
        setLightOpacity(definition.lightOpacity);
        setSoundType(definition.sound);
        if (definition.harvestLevel >= 0) setHarvestLevel(definition.harvestTool, definition.harvestLevel);
        setDefaultState(getDefaultState().withProperty(VARIANT, definition.defaultVariant));
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS);
        MODELS.put(this, definition.model);
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, VARIANT); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(VARIANT, Math.max(0, Math.min(definition.variants - 1, meta))); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(VARIANT); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public int damageDropped(IBlockState state) { return state.getValue(VARIANT); }
    @Override public void getSubBlocks(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> stacks) {
        for (int variant = 0; variant < definition.variants; variant++) stacks.add(new net.minecraft.item.ItemStack(this, 1, variant));
    }
    int variantCount() { return definition.variants; }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (definition.variants > 1) {
            int next = (state.getValue(VARIANT) + 1) % definition.variants;
            world.setBlockState(pos, state.withProperty(VARIANT, next), 3);
        }
        if (definition.interactEffect != null) {
            WorldServer serverWorld = world instanceof WorldServer ? (WorldServer) world : null;
            definition.interactEffect.apply(new DataContext(serverWorld, player, pos, world.rand, state.getValue(VARIANT)), player);
        }
        return true;
    }
}