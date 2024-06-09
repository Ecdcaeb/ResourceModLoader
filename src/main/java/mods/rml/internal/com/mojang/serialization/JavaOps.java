package mods.rml.internal.com.mojang.serialization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import mods.rml.internal.com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Ops for pure Java types.
 * This class MUST NOT discard any information (other than exact compound types) - there should be no data loss between 'create' and 'get' pairs.
 */
public class JavaOps implements DynamicOps<Object> {
    public static final JavaOps INSTANCE = new JavaOps();

    private JavaOps() {
    }

    @Override
    public Object empty() {
        return null;
    }

    @Override
    public Object emptyMap() {
        return Map.of();
    }

    @Override
    public Object emptyList() {
        return List.of();
    }

    @Override
    public <U> U convertTo(final DynamicOps<U> outOps, final Object input) {
        if (input == null) {
            return outOps.empty();
        }
        if (input instanceof Map) {
            return convertMap(outOps, input);
        }
        if (input instanceof ByteList) {
            final ByteList value = (ByteList) input;
            return outOps.createByteList(ByteBuffer.wrap(value.toByteArray()));
        }
        if (input instanceof IntList) {
            final IntList value = (IntList) input;
            return outOps.createIntList(IntStream.of(value.toArray(new int[0])));
        }
        if (input instanceof LongList) {
            final LongList value = (LongList) input;
            return outOps.createLongList(LongStream.of(value.toArray(new long[0])));
        }
        if (input instanceof List) {
            return convertList(outOps, input);
        }
        if (input instanceof String) {
            final String value = (String) input;
            return outOps.createString(value);
        }
        if (input instanceof Boolean) {
            final Boolean value = (Boolean) input;
            return outOps.createBoolean(value);
        }
        if (input instanceof Byte) {
            final Byte value = (Byte) input;
            return outOps.createByte(value);
        }
        if (input instanceof Short) {
            final Short value = (Short) input;
            return outOps.createShort(value);
        }
        if (input instanceof Integer) {
            final Integer value = (Integer) input;
            return outOps.createInt(value);
        }
        if (input instanceof Long) {
            final Long value = (Long) input;
            return outOps.createLong(value);
        }
        if (input instanceof Float) {
            final Float value = (Float) input;
            return outOps.createFloat(value);
        }
        if (input instanceof Double) {
            final Double value = (Double) input;
            return outOps.createDouble(value);
        }
        if (input instanceof Number) {
            final Number value = (Number) input;
            return outOps.createNumeric(value);
        }
        throw new IllegalStateException("Don't know how to convert " + input);
    }

    @Override
    public DataResult<Number> getNumberValue(final Object input) {
        if (input instanceof Number) {
            final Number value = (Number) input;
            return DataResult.success(value);
        }
        return DataResult.error(() -> "Not a number: " + input);
    }

    @Override
    public Object createNumeric(final Number value) {
        return value;
    }

    @Override
    public Object createByte(final byte value) {
        return value;
    }

    @Override
    public Object createShort(final short value) {
        return value;
    }

    @Override
    public Object createInt(final int value) {
        return value;
    }

    @Override
    public Object createLong(final long value) {
        return value;
    }

    @Override
    public Object createFloat(final float value) {
        return value;
    }

    @Override
    public Object createDouble(final double value) {
        return value;
    }

    @Override
    public DataResult<Boolean> getBooleanValue(final Object input) {
        if (input instanceof Boolean) {
            final Boolean value = (Boolean) input;
            return DataResult.success(value);
        }
        return DataResult.error(() -> "Not a boolean: " + input);
    }

    @Override
    public Object createBoolean(final boolean value) {
        return value;
    }

    @Override
    public DataResult<String> getStringValue(final Object input) {
        if (input instanceof String) {
            final String value = (String) input;
            return DataResult.success(value);
        }
        return DataResult.error(() -> "Not a string: " + input);
    }

    @Override
    public Object createString(final String value) {
        return value;
    }

    @Override
    public DataResult<Object> mergeToList(final Object input, final Object value) {
        if (input == empty()) {
            return DataResult.success(Lists.newArrayList(value));
        }
        if (input instanceof List) {
            final List<?> list = (List<?>) input;
            if (list.isEmpty()) {
                return DataResult.success(Lists.newArrayList(value));
            }
            return DataResult.success(ImmutableList.builder().addAll(list).add(value).build());
        }
        return DataResult.error(() -> "Not a list: " + input);
    }

    @Override
    public DataResult<Object> mergeToList(final Object input, final List<Object> values) {
        if (input == empty()) {
            return DataResult.success(values);
        }
        if (input instanceof List) {
            final List<?> list = (List<?>) input;
            if (list.isEmpty()) {
                return DataResult.success(values);
            }
            return DataResult.success(ImmutableList.builder().addAll(list).addAll(values).build());
        }
        return DataResult.error(() -> "Not a list: " + input);
    }

    @Override
    public DataResult<Object> mergeToMap(final Object input, final Object key, final Object value) {
        if (input == empty()) {
            final HashMap<Object, Object> map = new HashMap<>();
            map.put(key, value);
            return DataResult.success(map);
        }
        if (input instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) input;
            if (map.isEmpty()) {
                final HashMap<Object, Object> map1 = new HashMap<>();
                map1.put(key, value);
                return DataResult.success(map1);
            }

            final ImmutableMap.Builder<Object, Object> result = ImmutableMap.builder();
            result.putAll(map);
            result.put(key, value);
            return DataResult.success(result.build());
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public DataResult<Object> mergeToMap(final Object input, final Map<Object, Object> values) {
        if (input == empty()) {
            return DataResult.success(values);
        }
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            if (map.isEmpty()) {
                return DataResult.success(values);
            }
            final ImmutableMap.Builder<Object, Object> result = ImmutableMap.builder();
            result.putAll(map);
            result.putAll(values);
            return DataResult.success(result.build());
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    private static Map<Object, Object> mapLikeToMap(final MapLike<Object> values) {
        return values.entries().collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
    }

    @Override
    public DataResult<Object> mergeToMap(final Object input, final MapLike<Object> values) {
        if (input == empty()) {
            return DataResult.success(mapLikeToMap(values));
        }
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            if (map.isEmpty()) {
                return DataResult.success(mapLikeToMap(values));
            }

            final ImmutableMap.Builder<Object, Object> result = ImmutableMap.builder();
            result.putAll(map);
            values.entries().forEach(e -> result.put(e.getFirst(), e.getSecond()));
            return DataResult.success(result.build());
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    private static Stream<Pair<Object, Object>> getMapEntries(final Map<?, ?> input) {
        return input.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    }

    @Override
    public DataResult<Stream<Pair<Object, Object>>> getMapValues(final Object input) {
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            return DataResult.success(getMapEntries(map));
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public DataResult<Consumer<BiConsumer<Object, Object>>> getMapEntries(final Object input) {
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            return DataResult.success(map::forEach);
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public Object createMap(final Stream<Pair<Object, Object>> map) {
        return map.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
    }

    @Override
    public DataResult<MapLike<Object>> getMap(final Object input) {
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            return DataResult.success(
                new MapLike<>() {
                    @Nullable
                    @Override
                    public Object get(final Object key) {
                        return map.get(key);
                    }

                    @Nullable
                    @Override
                    public Object get(final String key) {
                        return map.get(key);
                    }

                    @Override
                    public Stream<Pair<Object, Object>> entries() {
                        return getMapEntries(map);
                    }

                    @Override
                    public String toString() {
                        return "MapLike[" + map + "]";
                    }
                }
            );
        }
        return DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public Object createMap(final Map<Object, Object> map) {
        return map;
    }

    @Override
    public DataResult<Stream<Object>> getStream(final Object input) {
        if (input instanceof List) {
            final List<?> list = (List<?>) input;
            return DataResult.success(list.stream().map(o -> o));
        }
        return DataResult.error(() -> "Not an list: " + input);
    }

    @Override
    public DataResult<Consumer<Consumer<Object>>> getList(final Object input) {
        if (input instanceof List) {
            final List<?> list = (List<?>) input;
            return DataResult.success(list::forEach);
        }
        return DataResult.error(() -> "Not an list: " + input);
    }

    @Override
    public Object createList(final Stream<Object> input) {
        return Lists.newArrayList(input.iterator());
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(final Object input) {
        if (input instanceof ByteList) {
            final ByteList value = (ByteList) input;
            return DataResult.success(ByteBuffer.wrap(value.toByteArray()));
        }
        return DataResult.error(() -> "Not a byte list: " + input);
    }

    @Override
    public Object createByteList(final ByteBuffer input) {
        // Set .limit to .capacity to match default method
        final ByteBuffer wholeBuffer = (ByteBuffer) input.duplicate().clear();
        final ByteArrayList result = new ByteArrayList();
        result.size(wholeBuffer.capacity());
        wholeBuffer.get(result.elements());
        return result;
    }

    @Override
    public DataResult<IntStream> getIntStream(final Object input) {
        if (input instanceof IntList) {
            final IntList value = (IntList) input;
            return DataResult.success(IntStream.of(value.toArray(new int[0])));
        }
        return DataResult.error(() -> "Not an int list: " + input);
    }

    @Override
    public Object createIntList(final IntStream input) {
        return IntArrayList.wrap(input.toArray());
    }

    @Override
    public DataResult<LongStream> getLongStream(final Object input) {
        if (input instanceof LongList) {
            final LongList value = (LongList) input;
            return DataResult.success(LongStream.of(value.toArray(new long[0])));
        }
        return DataResult.error(() -> "Not a long list: " + input);
    }

    @Override
    public Object createLongList(final LongStream input) {
        return LongArrayList.wrap(input.toArray());
    }

    @Override
    public Object remove(final Object input, final String key) {
        if (input instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) input;
            final Map<Object, Object> result = new LinkedHashMap<>(map);
            result.remove(key);
            HashMap<Object, Object> copyMap = new HashMap<>(result.size());
            copyMap.putAll(result);
            return copyMap;
        }
        return input;
    }

    @Override
    public RecordBuilder<Object> mapBuilder() {
        return new FixedMapBuilder<>(this);
    }

    @Override
    public String toString() {
        return "Java";
    }

    private static final class FixedMapBuilder<T> extends RecordBuilder.AbstractUniversalBuilder<T, ImmutableMap.Builder<T, T>> {
        public FixedMapBuilder(final DynamicOps<T> ops) {
            super(ops);
        }

        @Override
        protected ImmutableMap.Builder<T, T> initBuilder() {
            return ImmutableMap.builder();
        }

        @Override
        protected ImmutableMap.Builder<T, T> append(final T key, final T value, final ImmutableMap.Builder<T, T> builder) {
            return builder.put(key, value);
        }

        @Override
        protected DataResult<T> build(final ImmutableMap.Builder<T, T> builder, final T prefix) {
            final ImmutableMap<T, T> result = builder.build();
            return ops().mergeToMap(prefix, result);
        }
    }
}
