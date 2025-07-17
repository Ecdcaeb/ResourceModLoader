package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.utils.PrimitiveHelper;
import rml.loader.deserialize.Deserializer;

public class ConfigDoubleArray extends ConfigPrimitive{

    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "doubles"))
            .require(String.class, "name")
            .require(Double[].class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigDoubleArray(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            PrimitiveHelper.cast(context.get(Double[].class, "defaultValue")))).build();


    protected final double[] defaultVal;
    protected ConfigDoubleArray(ConfigDescription parentIn, String nameIn, double[] defaultVal) {
        super(parentIn, nameIn);
        this.defaultVal = defaultVal;
    }

    @Override
    public String createDescription() {
        return "[D";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, double[] d) {
        pushInt(methodVisitor, d.length);
        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
        for (int i = 0; i < d.length; i ++) {
            methodVisitor.visitInsn(Opcodes.DUP);
            pushInt(methodVisitor, i);
            methodVisitor.visitLdcInsn(d[i]);
            methodVisitor.visitInsn(Opcodes.DASTORE);
        }
    }
}
