package rml.loader.content.gameplay;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chest-container UI with station-specific server-side recipe execution. */
public final class StationTileEntity extends TileEntityChest {
    private static volatile Map<ResourceLocation, MachineRecipe> recipes = new LinkedHashMap<>();
    private static final int[] OUTPUT_SLOTS = {3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private int burnTime;
    private int cookProgress;
    private int totalCookTime;
    private ResourceLocation activeRecipe;

    static void setRecipes(Map<ResourceLocation, MachineRecipe> values) { recipes = new LinkedHashMap<>(values); }

    @Override
    public void update() {
        if (world == null) return;
        super.update();
        if (world.isRemote || !(world instanceof WorldServer)) return;
        boolean dirty = false;
        if (burnTime > 0) { burnTime--; dirty = true; }
        DataContext context = new DataContext((WorldServer) world, null, pos, world.rand, 0);
        MachineRecipe recipe = findRecipe(context);
        ItemStack recipeResult = recipe == null ? ItemStack.EMPTY : recipe.resultFor(getStackInSlot(0), getStackInSlot(1), getStackInSlot(2), context);
        if (recipe == null || !canOutput(recipeResult)) {
            if (cookProgress != 0 || activeRecipe != null) { cookProgress = 0; activeRecipe = null; dirty = true; }
            if (dirty) markDirty();
            return;
        }
        boolean cooking = isCooking(recipe.operation);
        if (cooking && burnTime == 0 && !consumeFuel()) {
            cookProgress = 0;
            activeRecipe = recipe.id;
            markDirty();
            return;
        }
        if (!recipe.id.equals(activeRecipe)) {
            activeRecipe = recipe.id;
            cookProgress = 0;
            totalCookTime = cooking ? recipe.cookingTime : 1;
        }
        cookProgress++;
        dirty = true;
        if (cookProgress >= Math.max(1, totalCookTime)) {
            complete(recipe, recipeResult);
            cookProgress = 0;
            activeRecipe = null;
        }
        if (dirty) markDirty();
    }

    private MachineRecipe findRecipe(DataContext context) {
        String station = getBlockType() == null || getBlockType().getRegistryName() == null ? "" : getBlockType().getRegistryName().getPath();
        ItemStack selection = getStackInSlot(4);
        for (MachineRecipe recipe : recipes.values()) {
            if (!matchesStation(station, recipe.operation)) continue;
            boolean matches = true;
            for (int index = 0; index < recipe.ingredients.length; index++) {
                ItemStack input = getStackInSlot(index);
                if (input.isEmpty() || !recipe.ingredients[index].test(input, context)) { matches = false; break; }
            }
            if (matches) {
                ItemStack result = recipe.resultFor(getStackInSlot(0), getStackInSlot(1), getStackInSlot(2), context);
                if (selection.isEmpty() || ItemStack.areItemsEqual(selection, result)) return recipe;
            }
        }
        return null;
    }

    private boolean canOutput(ItemStack result) {
        int remaining = result.getCount();
        for (int index : OUTPUT_SLOTS) {
            ItemStack output = getStackInSlot(index);
            if (output.isEmpty()) remaining -= Math.min(result.getMaxStackSize(), getInventoryStackLimit());
            else if (ItemStack.areItemsEqual(output, result) && ItemStack.areItemStackTagsEqual(output, result)) {
                remaining -= Math.min(result.getMaxStackSize(), getInventoryStackLimit()) - output.getCount();
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void insertOutput(ItemStack result) {
        int remaining = result.getCount();
        for (int index : OUTPUT_SLOTS) {
            ItemStack output = getStackInSlot(index);
            if (output.isEmpty() || !ItemStack.areItemsEqual(output, result) || !ItemStack.areItemStackTagsEqual(output, result)) continue;
            int moved = Math.min(remaining, Math.min(output.getMaxStackSize(), getInventoryStackLimit()) - output.getCount());
            if (moved > 0) { output.grow(moved); remaining -= moved; }
            if (remaining == 0) return;
        }
        for (int index : OUTPUT_SLOTS) {
            if (!getStackInSlot(index).isEmpty()) continue;
            ItemStack split = result.copy();
            split.setCount(Math.min(remaining, Math.min(split.getMaxStackSize(), getInventoryStackLimit())));
            setInventorySlotContents(index, split);
            remaining -= split.getCount();
            if (remaining == 0) return;
        }
    }


    private boolean consumeFuel() {
        ItemStack fuel = getStackInSlot(5);
        int burn = TileEntityFurnace.getItemBurnTime(fuel);
        if (burn <= 0) return false;
        burnTime = burn;
        ItemStack remainder = fuel.getItem().getContainerItem(fuel);
        if (fuel.getCount() > 1) fuel.shrink(1);
        else setInventorySlotContents(5, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        return true;
    }

    private void complete(MachineRecipe recipe, ItemStack crafted) {
        ItemStack result = crafted.copy();
        if ("smithing_transform".equals(recipe.operation)) {
            ItemStack base = getStackInSlot(1);
            net.minecraft.nbt.NBTTagCompound copied = base.hasTagCompound() ? base.getTagCompound().copy() : new net.minecraft.nbt.NBTTagCompound();
            if (result.hasTagCompound()) copied.merge(result.getTagCompound().copy());
            result.setItemDamage(base.getItemDamage());
            if (!copied.isEmpty()) result.setTagCompound(copied);
        }
        insertOutput(result);
        for (int index = 0; index < recipe.ingredients.length; index++) {
            ItemStack input = getStackInSlot(index);
            input.shrink(1);
            if (input.isEmpty()) setInventorySlotContents(index, ItemStack.EMPTY);
        }
        if (recipe.experience > 0) {
            int points = MathHelper.floor(recipe.experience);
            if (world.rand.nextFloat() < recipe.experience - points) points++;
            if (points > 0) world.spawnEntity(new EntityXPOrb(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, points));
        }
    }

    private static boolean isCooking(String operation) {
        return "blasting".equals(operation) || "smoking".equals(operation) || "campfire_cooking".equals(operation);
    }

    private static boolean matchesStation(String station, String operation) {
        switch (operation) {
            case "blasting": return "blast_furnace".equals(station);
            case "smoking": return "smoker".equals(station);
            case "campfire_cooking": return "campfire".equals(station);
            case "stonecutting": return "stonecutter".equals(station);
            case "smithing_transform":
            case "smithing_trim": return "smithing_table".equals(station);
            case "crafting_transmute": return "smithing_table".equals(station);
            default: return false;
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("BurnTime", burnTime); tag.setInteger("CookProgress", cookProgress); tag.setInteger("TotalCookTime", totalCookTime);
        if (activeRecipe != null) tag.setString("ActiveRecipe", activeRecipe.toString());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        burnTime = tag.getInteger("BurnTime"); cookProgress = tag.getInteger("CookProgress"); totalCookTime = tag.getInteger("TotalCookTime");
        activeRecipe = tag.hasKey("ActiveRecipe") ? new ResourceLocation(tag.getString("ActiveRecipe")) : null;
    }
}