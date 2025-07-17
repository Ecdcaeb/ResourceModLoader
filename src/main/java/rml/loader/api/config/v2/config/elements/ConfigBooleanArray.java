package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.utils.PrimitiveHelper;
import rml.loader.deserialize.Deserializer;

public class ConfigBooleanArray extends ConfigPrimitive {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "bools"))
            .require(String.class, "name")
            .require(Boolean[].class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigBooleanArray(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            PrimitiveHelper.cast(context.get(Boolean[].class, "defaultValue")))).build();

    protected boolean[] defaultVal;

    public ConfigBooleanArray(ConfigDescription description, String nameIn, boolean[] defaultVal) {
        super(description, nameIn);
        this.defaultVal = defaultVal;
    }

    @Override
    public String createDescription() {
        return "[Z";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, boolean[] booleans) {
        pushInt(methodVisitor, booleans.length);
        methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
        for (int i = 0; i < booleans.length; i ++) {
            methodVisitor.visitInsn(Opcodes.DUP);
            pushInt(methodVisitor, i);
            ConfigBoolean.createToStackBoolean(methodVisitor, booleans[i]);
            methodVisitor.visitInsn(Opcodes.BASTORE);
        }
    }

}
