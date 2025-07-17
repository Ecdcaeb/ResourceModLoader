package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

import java.util.Arrays;
import java.util.Objects;

public class ConfigDescription {
    public static final AbstractDeserializer<ConfigDescription> DESERIALIZER = Deserializer.named(ConfigDescription.class, new ResourceLocation("rml", "config_description"))
            .optionalDefault(String.class, "langKey", null)
            .optionalDefault(String.class, "displayName", null)
            .optionalDefault(String[].class, "comment", null)
            .optionalDefault(Boolean.class, "requiresMcRestart", Boolean.FALSE)
            .optionalDefault(Boolean.class, "requiresWorldRestart", Boolean.FALSE)
            .optionalDefault(Boolean.class, "slidingOption", Boolean.FALSE)
            .decode(context -> new ConfigDescription(
                    context.get(String.class, "langKey"),
                    context.get(String.class, "displayName"),
                    context.get(String[].class, "comment"),
                    context.get(Boolean.class, "requiresMcRestart"),
                    context.get(Boolean.class, "requiresWorldRestart"),
                    context.get(Boolean.class, "slidingOption")
            ))
            .markDefault().build();

    public static final ConfigDescription EMPTY = new ConfigDescription(null, null, null, false, false, false);



    public final String langKey;
    public final String displayName;
    public final String[] comment;
    public final boolean requiresMcRestart, requiresWorldRestart, slidingOption;

    public ConfigDescription(String langKey, String displayName, String[] comment, boolean requiresMcRestart, boolean requiresWorldRestart, boolean slidingOption) {
        this.langKey = langKey;
        this.displayName = displayName;
        this.comment = comment;
        this.requiresMcRestart = requiresMcRestart;
        this.requiresWorldRestart = requiresWorldRestart;
        this.slidingOption = slidingOption;
    }

    @Override
    public String toString() {
        return "ConfigDescription{" +
                "langKey='" + langKey + '\'' +
                ", displayName='" + displayName + '\'' +
                ", comment=" + Arrays.toString(comment) +
                ", requiresMcRestart=" + requiresMcRestart +
                ", requiresWorldRestart=" + requiresWorldRestart +
                ", slidingOption=" + slidingOption +
                '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ConfigDescription)) return false;

        ConfigDescription that = (ConfigDescription) o;
        return requiresMcRestart == that.requiresMcRestart && requiresWorldRestart == that.requiresWorldRestart && slidingOption == that.slidingOption && Objects.equals(langKey, that.langKey) && Objects.equals(displayName, that.displayName) && Arrays.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(langKey);
        result = 31 * result + Objects.hashCode(displayName);
        result = 31 * result + Arrays.hashCode(comment);
        result = 31 * result + Boolean.hashCode(requiresMcRestart);
        result = 31 * result + Boolean.hashCode(requiresWorldRestart);
        result = 31 * result + Boolean.hashCode(slidingOption);
        return result;
    }
}
