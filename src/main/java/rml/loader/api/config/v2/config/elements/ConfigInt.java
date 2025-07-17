package rml.loader.api.config.v2.config.elements;

import crafttweaker.annotations.ZenRegister;
import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;
import stanhebben.zenscript.annotations.ZenClass;

@ZenRegister
@ZenClass("mods.zenutils.config.elements.ConfigInt")
public class ConfigInt extends ConfigPrimitive{
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "int"))
            .require(String.class, "name")
            .require(Integer.class, "defaultValue")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigInt(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(Integer.class, "defaultValue"))).build();


    protected final int defaultVal;

    public ConfigInt(ConfigDescription parentIn, String nameIn, int defaultVal) {
        super(parentIn, nameIn);
        this.defaultVal = defaultVal;
    }

    public int getDefaultVal() {
        return defaultVal;
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        pushInt(methodVisitor, this.defaultVal);
    }

    public static void createToStack(MethodVisitor methodVisitor, int i) {
        pushInt(methodVisitor, i);
    }

    @Override
    public String createDescription() {
        return "I";
    }
}
