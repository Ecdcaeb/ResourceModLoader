package mods.rml.api.world.values.items;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/13 14:59
 **/
public class InventoryLiving implements IInventory
{
    // 0 1 2 3 4 5 //EntityEquipmentSlot
    // 6 ....... size + 5
    private InventoryBasic inventory;
    public EntityLivingBase living;

    public InventoryLiving(EntityLivingBase livingIn, int size)
    {
        this.living = livingIn;
        this.inventory = new InventoryBasic(livingIn.getDisplayName(), size);
    }

    @Override
    public int getSizeInventory() {
        return 6 + this.inventory.getSizeInventory();
    }

    @Override
    public boolean isEmpty() {
        if (!this.inventory.isEmpty())return false;
        else {
            for(EntityEquipmentSlot slot : EntityEquipmentSlot.values()){
                if (!living.getItemStackFromSlot(slot).isEmpty())return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < 6) {
            return this.living.getItemStackFromSlot(getEntityEquipmentSlotFromIndex(index));
        }
        else return this.inventory.getStackInSlot(index - 6);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 6) {
            ItemStack stack = this.living.getItemStackFromSlot(getEntityEquipmentSlotFromIndex(index));
            return stack.isEmpty() ?  ItemStack.EMPTY : stack.splitStack(count) ;
        }
        else return this.inventory.decrStackSize(index - 6, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 6) {
            ItemStack stack = this.living.getItemStackFromSlot(getEntityEquipmentSlotFromIndex(index));
            this.living.setItemStackToSlot(getEntityEquipmentSlotFromIndex(index), ItemStack.EMPTY);
            return stack;
        }
        else return this.inventory.removeStackFromSlot(index - 6);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 6) {
            this.living.setItemStackToSlot(getEntityEquipmentSlotFromIndex(index), stack);
        }
        else this.inventory.setInventorySlotContents(index - 6, stack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        this.inventory.markDirty();
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {

    }

    @Override
    public String getName() {
        return living.getName();
    }

    @Override
    public boolean hasCustomName() {
        return living.hasCustomName();
    }

    @Override
    public ITextComponent getDisplayName() {
        return living.getDisplayName();
    }

    public static EntityEquipmentSlot getEntityEquipmentSlotFromIndex(int i){
        if (i==0) return EntityEquipmentSlot.MAINHAND;
        else if (i==1) return EntityEquipmentSlot.OFFHAND;
        else if (i==2) return EntityEquipmentSlot.FEET;
        else if (i==3) return EntityEquipmentSlot.LEGS;
        else if (i==4) return EntityEquipmentSlot.CHEST;
        else if (i==5) return EntityEquipmentSlot.HEAD;
        else throw new IllegalArgumentException();
    }
}
