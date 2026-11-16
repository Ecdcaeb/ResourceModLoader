package rml.deserializer.value;

import java.util.Objects;
import java.util.Random;
import java.util.function.ToIntFunction;

/** A reusable random integer distribution, compiled once with inclusive output bounds. */
public final class IntProvider {
    private final int minimum;
    private final int maximum;
    private final ToIntFunction<Random> sampler;

    public IntProvider(int minimum, int maximum, ToIntFunction<Random> sampler) {
        if (minimum > maximum) throw new IllegalArgumentException("Integer provider minimum exceeds maximum");
        this.minimum = minimum;
        this.maximum = maximum;
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    public int sample(Random random) { return sampler.applyAsInt(random); }
    public int getMinValue() { return minimum; }
    public int getMaxValue() { return maximum; }
}
