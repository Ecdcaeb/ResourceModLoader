package mods.rml.api.world.values.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/13 14:45
 **/
public class SlotEquipment extends Slot {
    public EntityEquipmentSlot equipmentSlot;
    public SlotEquipment(IInventory inventoryIn, int index, int xPosition, int yPosition, EntityEquipmentSlot equipmentSlot) {
        super(inventoryIn, index, xPosition, yPosition);
        this.equipmentSlot = equipmentSlot;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof ItemArmor && ((ItemArmor)stack.getItem()).armorType == this.equipmentSlot;
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }

    public static class NonNullListInventory implements IInventory{
        public NonNullList<ItemStack> nonNullList;
        public String name;
        public NonNullListInventory(NonNullList<ItemStack> nonNullList, String name){
            this.nonNullList = nonNullList;
            this.name = name;
        }

        @Override
        public int getSizeInventory() {
            return nonNullList.size();
        }

        @Override
        public boolean isEmpty() {
            return nonNullList.isEmpty() || nonNullList.stream().noneMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return nonNullList.get(index);
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            return ItemStackHelper.getAndSplit(this.nonNullList, index, count);
        }

        @Override
        public ItemStack removeStackFromSlot(int index) {
            return ItemStackHelper.getAndRemove(this.nonNullList, index);
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            this.nonNullList.set(index, stack);
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {
            //NO-OP
        }

        @Override
        public boolean isUsableByPlayer(EntityPlayer player) {
            return false;//NO-OP
        }

        @Override
        public void openInventory(EntityPlayer player) {
            //NO-OP
        }

        @Override
        public void closeInventory(EntityPlayer player) {
            //NO-OP
        }

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return true;
        }

        @Override
        public int getField(int id) {
            return 0;//NO-OP
        }

        @Override
        public void setField(int id, int value) {
            //NO-OP
        }

        @Override
        public int getFieldCount() {
            return 0;//NO-OP
        }

        @Override
        public void clear() {
            this.nonNullList.clear();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasCustomName() {
            return false;
        }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentString(name);
        }
    }
}
