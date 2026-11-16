package rml.loader.runtime.function;

import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Native NBT path matching contract; uses real 1.12 tags without mocks. */
public final class FunctionDataPathContractTest {
    private FunctionDataPathContractTest() { }

    public static void main(String[] args) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound first = new NBTTagCompound();
        first.setTag("Slot", new NBTTagByte((byte) 1));
        NBTTagCompound display = new NBTTagCompound();
        display.setTag("Name", new NBTTagString("rare"));
        first.setTag("Display", display);
        first.setTag("Count", new NBTTagInt(2));
        NBTTagCompound second = new NBTTagCompound();
        second.setTag("Slot", new NBTTagByte((byte) 2));
        second.setTag("Count", new NBTTagInt(4));
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(first);
        inventory.appendTag(second);
        root.setTag("Inventory", inventory);

        equal("rare", ((NBTTagString) FunctionDataPath.get(root, "Inventory[{Display:{Name:\"rare\"}}].Display.Name")).getString());
        FunctionDataPath.set(root, "Inventory[{Slot:1b}].Count", new NBTTagInt(7));
        equal(7, ((NBTTagInt) FunctionDataPath.get(root, "Inventory[{Slot:1b}].Count")).getInt());
        check(FunctionDataPath.remove(root, "Inventory[{Display:{Name:\"rare\"}}]"), "compound selector removes matching list elements");
        equal(1, root.getTagList("Inventory", 10).tagCount());

        FunctionDataPath.set(root, "\"odd.key\".\"say\\\"hi\"", new NBTTagString("quoted"));
        equal("quoted", ((NBTTagString) FunctionDataPath.get(root, "\"odd.key\".\"say\\\"hi\"")).getString());
        System.out.println("FunctionDataPathContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
