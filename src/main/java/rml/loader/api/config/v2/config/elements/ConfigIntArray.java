package rml.loader.api.config.v2.config.elements;

import crafttweaker.annotations.ZenRegister;
import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.utils.PrimitiveHelper;
import rml.loader.deserialize.Deserializer;
import stanhebben.zenscript.annotations.ZenClass;

@ZenRegister
@ZenClass("mods.zenutils.config.elements.ConfigIntArray")
public class ConfigIntArray extends ConfigPrimitive {

    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "int"))
            .require(String.class, "name")
            .require(Integer[].class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigIntArray(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            PrimitiveHelper.cast(context.get(Integer[].class, "defaultValue")))).build();

    protected final int[] defaultVal;

    protected ConfigIntArray(ConfigDescription parentIn, String nameIn, int[] defaultVal) {
        super(parentIn, nameIn);
        this.defaultVal = defaultVal;
    }

    @Override
    public String createDescription() {
        return "[I";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, int[] is) {
        pushInt(methodVisitor, is.length);
        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
        for (int i = 0; i < is.length; i ++) {
            methodVisitor.visitInsn(Opcodes.DUP);
            pushInt(methodVisitor, i);
            pushInt(methodVisitor, is[i]);
            methodVisitor.visitInsn(Opcodes.IASTORE);
        }
    }
}
