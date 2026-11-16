package rml.loader.runtime;

import net.minecraft.command.CommandException;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Literal SNBT values accepted by enhanced data commands. */
public final class FunctionNbtContractTest {
    private FunctionNbtContractTest() { }

    public static void main(String[] args) throws Exception {
        check(((NBTTagByte) value("1b")).getByte() == 1, "byte literals must retain their NBT type");
        check("survey notes".equals(((NBTTagString) value("\"survey notes\"")).getString()), "quoted strings must retain spaces");
        NBTTagList list = (NBTTagList) value("[{Slot:1b},{Slot:2b}]");
        check(list.getCompoundTagAt(1).getByte("Slot") == 2, "list values must preserve nested compounds");
        check(((NBTTagIntArray) value("[I;3,5,8]")).getIntArray()[2] == 8, "typed arrays must retain their values");
        check(((NBTTagCompound) value("{ready:1b}")).getBoolean("ready"), "compound values must remain supported");
        try {
            value("1,extra:2");
            throw new AssertionError("multiple root values must be rejected");
        } catch (CommandException expected) { }
        System.out.println("FunctionNbtContractTest passed");
    }

    private static net.minecraft.nbt.NBTBase value(String value) throws CommandException {
        return CompiledFunctionEngine.parseNbt(new String[]{"ignored", value}, 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
