package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigRangedInt extends ConfigInt {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "ranged_int"))
            .require(String.class, "name")
            .require(Integer.class, "defaultValue")
            .optionalDefault(Integer.class, "min", Integer.MIN_VALUE)
            .optionalDefault(Integer.class, "max", Integer.MAX_VALUE)
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigRangedInt(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(Integer.class, "defaultValue"),
                            context.get(Integer.class, "min"),
                            context.get(Integer.class, "max"))).build();

    protected final int min;
    protected final int max;
    public ConfigRangedInt(ConfigDescription parentIn, String nameIn, int defaultVal, int min, int max) {
        super(parentIn, nameIn, defaultVal);
        this.min = min;
        this.max = max;
    }

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }
}
