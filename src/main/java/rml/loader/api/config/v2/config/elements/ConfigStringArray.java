package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigStringArray extends ConfigPrimitive {

    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "strings"))
            .require(String.class, "name")
            .require(String[].class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context -> new ConfigStringArray(
                    context.get(ConfigDescription.class, "display"),
                    context.get(String.class, "name"),
                    context.get(String[].class, "defaultValue"))).build();


    protected final String[] defaultVal;
    protected ConfigStringArray(ConfigDescription parentIn, String nameIn, String[] defaultVal) {
        super(parentIn, nameIn);
        this.defaultVal = defaultVal;
    }

    public String[] getDefaultVal() {
        return defaultVal;
    }

    @Override
    public String createDescription() {
        return "[Ljava/lang/String;";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, String[] s) {
        pushInt(methodVisitor, s.length);
        methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < s.length; i ++) {
            methodVisitor.visitInsn(Opcodes.DUP);
            pushInt(methodVisitor, i);
            methodVisitor.visitLdcInsn(s[i]);
            methodVisitor.visitInsn(Opcodes.AASTORE);
        }
    }
}
