package rml.loader.content.gameplay;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeDataItem extends Item {
    static final Map<Item, ResourceLocation> MODELS = new LinkedHashMap<>();
    private final NativeItemDefinition definition;

    NativeDataItem(NativeItemDefinition definition) {
        this.definition = definition;
        setRegistryName(definition.id);
        setTranslationKey(definition.id.getNamespace() + "." + definition.id.getPath());
        setMaxStackSize(definition.maxStackSize);
        if (definition.maxDamage > 0) setMaxDamage(definition.maxDamage);
        if (!definition.harvestTool.isEmpty() && definition.harvestLevel >= 0) setHarvestLevel(definition.harvestTool, definition.harvestLevel);
        setCreativeTab(tab(definition.creativeTab));
        MODELS.put(this, definition.model);
    }

    private static CreativeTabs tab(String name) {
        switch (name) {
            case "building_blocks": return CreativeTabs.BUILDING_BLOCKS;
            case "decorations": return CreativeTabs.DECORATIONS;
            case "redstone": return CreativeTabs.REDSTONE;
            case "transportation": return CreativeTabs.TRANSPORTATION;
            case "tools": return CreativeTabs.TOOLS;
            case "combat": return CreativeTabs.COMBAT;
            case "brewing": return CreativeTabs.BREWING;
            case "materials": return CreativeTabs.MATERIALS;
            case "food": return CreativeTabs.FOOD;
            case "misc": return CreativeTabs.MISC;
            default: throw new IllegalArgumentException("Unknown creative tab: " + name);
        }
    }

    @Override public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return !definition.harvestTool.isEmpty() && state.getBlock().isToolEffective(definition.harvestTool, state) ? definition.destroySpeed : super.getDestroySpeed(stack, state);
    }

    @Override public boolean canHarvestBlock(IBlockState state) {
        String tool = state.getBlock().getHarvestTool(state);
        return tool == null || tool.isEmpty() || (tool.equals(definition.harvestTool)
                && definition.harvestLevel >= state.getBlock().getHarvestLevel(state));
    }

    @Override public boolean onBlockDestroyed(ItemStack stack, World world, IBlockState state, BlockPos pos, EntityLivingBase user) {
        if (!world.isRemote && state.getBlockHardness(world, pos) != 0 && !definition.harvestTool.isEmpty()) stack.damageItem(1, user);
        return true;
    }

    @Override public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (!attacker.world.isRemote && !definition.harvestTool.isEmpty()) stack.damageItem("sword".equals(definition.harvestTool) ? 1 : 2, attacker);
        return true;
    }

    @Override public int getMaxItemUseDuration(ItemStack stack) { return definition.useDuration; }
    @Override public EnumAction getItemUseAction(ItemStack stack) {
        switch (definition.useAction) {
            case "eat": return EnumAction.EAT;
            case "drink": return EnumAction.DRINK;
            case "block": return EnumAction.BLOCK;
            case "bow": return EnumAction.BOW;
            default: return EnumAction.NONE;
        }
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!definition.equipmentSlot.isEmpty()) {
            equip(player, hand, stack);
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
        if (definition.nutrition > 0) {
            if (!definition.alwaysEdible && !player.canEat(false)) return new ActionResult<>(EnumActionResult.FAIL, stack);
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        if (definition.useDuration > 0 && !"none".equals(definition.useAction)) {
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    private void equip(EntityPlayer player, EnumHand hand, ItemStack held) {
        net.minecraft.inventory.EntityEquipmentSlot slot = equipmentSlot(definition.equipmentSlot);
        ItemStack previous = player.getItemStackFromSlot(slot);
        ItemStack equipped = held.copy();
        equipped.setCount(1);
        player.setItemStackToSlot(slot, equipped);
        held.shrink(1);
        if (!previous.isEmpty() && !player.inventory.addItemStackToInventory(previous)) player.dropItem(previous, false);
    }

    @Override public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase user) {
        if (user instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) user;
            if (definition.nutrition > 0) player.getFoodStats().addStats(definition.nutrition, definition.saturation);
            if (!player.capabilities.isCreativeMode && (definition.nutrition > 0 || "drink".equals(definition.useAction))) stack.shrink(1);
        }
        if (definition.useEffect != null && !world.isRemote) {
            WorldServer serverWorld = world instanceof WorldServer ? (WorldServer) world : null;
            BlockPos origin = user.getPosition();
            definition.useEffect.apply(new DataContext(serverWorld, user, origin, world.rand, 0), user);
        }
        return stack;
    }

    private static net.minecraft.inventory.EntityEquipmentSlot equipmentSlot(String name) {
        switch (name) {
            case "mainhand": return net.minecraft.inventory.EntityEquipmentSlot.MAINHAND;
            case "offhand": return net.minecraft.inventory.EntityEquipmentSlot.OFFHAND;
            case "head": return net.minecraft.inventory.EntityEquipmentSlot.HEAD;
            case "chest": return net.minecraft.inventory.EntityEquipmentSlot.CHEST;
            case "legs": return net.minecraft.inventory.EntityEquipmentSlot.LEGS;
            case "feet": return net.minecraft.inventory.EntityEquipmentSlot.FEET;
            default: throw new IllegalStateException("Invalid equipment slot " + name);
        }
    }
}