package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigDouble extends ConfigPrimitive {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "double"))
            .require(String.class, "name")
            .require(Double.class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context -> new ConfigDouble(
                    context.get(ConfigDescription.class, "display"),
                    context.get(String.class, "name"),
                    context.get(Double.class, "defaultValue"))).build();


    protected final double defaultVal;

    public ConfigDouble(ConfigDescription description, String nameIn, double defaultVal) {
        super(description, nameIn);
        this.defaultVal = defaultVal;
    }

    public double getDefaultVal() {
        return defaultVal;
    }

    @Override
    public String createDescription() {
        return "D";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultVal);
    }

    public static void createToStack0(MethodVisitor methodVisitor, double d) {
        methodVisitor.visitLdcInsn(d);
    }
}
