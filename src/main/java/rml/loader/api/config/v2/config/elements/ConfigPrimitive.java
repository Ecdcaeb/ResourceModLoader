package rml.loader.api.config.v2.config.elements;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class ConfigPrimitive extends ConfigElement {
    protected ConfigPrimitive(ConfigDescription description, String nameIn) {
        super(nameIn, description);
    }

    public abstract String createDescription();

    public abstract void createToStack(MethodVisitor methodVisitor);

    public String createSignature(){
        return null;
    }

    private static final int[] iconsts = new int[] {
            Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5
    };

    public static void pushInt(MethodVisitor methodVisitor, int val) {
        if (val > 32767 || val < -32768) {
            methodVisitor.visitLdcInsn(val);
        } else if (val > 127 || val < -128) {
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, val);
        } else if (val > 5 || val < -1) {
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, val);
        } else methodVisitor.visitInsn(iconsts[val+1]);
    }
}
