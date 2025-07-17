package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigString extends ConfigPrimitive {

    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "string"))
            .require(String.class, "name")
            .require(String.class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigString(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(String.class, "defaultValue"))).build();

    protected final String defaultVal;
    protected ConfigString(ConfigDescription parentIn, String nameIn, String defaultVal) {
        super(parentIn, nameIn);
        this.defaultVal = defaultVal;
    }

    public String getDefaultVal() {
        return defaultVal;
    }

    @Override
    public String createDescription() {
        return "Ljava/lang/String;";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, String s) {
        methodVisitor.visitLdcInsn(s);
    }
}
