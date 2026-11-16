package rml.deserializer.value;

/** Inclusive integer bounds, including unbounded ends represented by the native integer limits. */
public final class IntRange {
    private final int min;
    private final int max;

    public IntRange(int min, int max) {
        if (min > max) throw new IllegalArgumentException("Range minimum exceeds maximum");
        this.min = min;
        this.max = max;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }
    public boolean test(int value) { return value >= min && value <= max; }
}
