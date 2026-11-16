package rml.deserializer.value;

/** Inclusive numeric bounds. NaN never satisfies a range. */
public final class DoubleRange {
    private final double min;
    private final double max;

    public DoubleRange(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || min > max) throw new IllegalArgumentException("Invalid numeric range");
        this.min = min;
        this.max = max;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public boolean test(double value) { return value >= min && value <= max; }
}
