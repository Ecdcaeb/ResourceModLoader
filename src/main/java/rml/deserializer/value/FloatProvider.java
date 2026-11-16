package rml.deserializer.value;

import java.util.Objects;
import java.util.Random;

/** A compiled random float distribution; sampling does not allocate boxed values. */
public final class FloatProvider {
    @FunctionalInterface public interface Sampler { float sample(Random random); }
    private final float minimum;
    private final float maximum;
    private final Sampler sampler;

    public FloatProvider(float minimum, float maximum, Sampler sampler) {
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum > maximum)
            throw new IllegalArgumentException("Float provider requires finite, ordered bounds");
        this.minimum = minimum;
        this.maximum = maximum;
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    public float sample(Random random) { return sampler.sample(random); }
    public float getMinValue() { return minimum; }
    public float getMaxValue() { return maximum; }
}
