package rml.deserializer.value;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Immutable weighted values; sampling allocates nothing and retains full integer precision. */
public final class WeightedList<T> {
    private final Object[] values;
    private final long[] cumulative;
    private final long totalWeight;

    public WeightedList(List<? extends T> values, long[] weights) {
        if (values.isEmpty() || values.size() != weights.length) throw new IllegalArgumentException("Weights must match a nonempty value list");
        this.values = values.toArray();
        this.cumulative = new long[weights.length];
        long total = 0;
        for (int index = 0; index < weights.length; index++) {
            Objects.requireNonNull(this.values[index], "weighted value");
            if (weights[index] <= 0) throw new IllegalArgumentException("Weights must be positive integers");
            total = Math.addExact(total, weights[index]);
            cumulative[index] = total;
        }
        totalWeight = total;
    }

    public int size() { return values.length; }
    public long getTotalWeight() { return totalWeight; }
    public long getWeight(int index) { return cumulative[index] - (index == 0 ? 0 : cumulative[index - 1]); }

    @SuppressWarnings("unchecked")
    public T get(int index) { return (T) values[index]; }

    public T sample(Random random) {
        long bits;
        long selected;
        do {
            bits = random.nextLong() >>> 1;
            selected = bits % totalWeight;
        } while (bits - selected + totalWeight - 1 < 0);
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (selected < cumulative[middle]) high = middle;
            else low = middle + 1;
        }
        return get(low);
    }
}
