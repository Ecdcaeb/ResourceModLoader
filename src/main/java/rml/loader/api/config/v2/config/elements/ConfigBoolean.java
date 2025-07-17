package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigBoolean extends ConfigPrimitive {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "bool"))
            .require(String.class, "name")
            .require(Boolean.class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigBoolean(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(Boolean.class, "defaultValue"))).build();

    protected boolean defaultVal;

    public ConfigBoolean(ConfigDescription description, String nameIn, boolean defaultVal) {
        super(description, nameIn);
        this.defaultVal = defaultVal;
    }

    @Override
    public String createDescription() {
        return "Z";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStackBoolean(methodVisitor, this.defaultVal);
    }

    public static void createToStackBoolean(MethodVisitor methodVisitor, boolean defaultVal) {
        if (defaultVal) {
            methodVisitor.visitInsn(Opcodes.ICONST_1);
        } else methodVisitor.visitInsn(Opcodes.ICONST_0);
    }
}
