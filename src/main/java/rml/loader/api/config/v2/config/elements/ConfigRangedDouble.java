package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ConfigRangedDouble extends ConfigDouble {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "ranged_double"))
            .require(String.class, "name")
            .require(Double.class, "defaultValue")
            .optionalDefault(Double.class, "min", Double.MIN_VALUE)
            .optionalDefault(Double.class, "max", Double.MAX_VALUE)
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigRangedDouble(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(Double.class, "defaultValue"),
                            context.get(Double.class, "min"),
                            context.get(Double.class, "max"))).build();

    protected final double min;
    protected final double max;
    protected ConfigRangedDouble(ConfigDescription parentIn, String nameIn, double defaultVal, double min, double max) {
        super(parentIn, nameIn, defaultVal);
        this.min = min;
        this.max = max;
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }
}
