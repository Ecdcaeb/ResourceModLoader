// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.serialization.codecs;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import mods.rml.internal.com.mojang.datafixers.util.Pair;
import mods.rml.internal.com.mojang.datafixers.util.Unit;
import mods.rml.internal.com.mojang.serialization.Codec;
import mods.rml.internal.com.mojang.serialization.DataResult;
import mods.rml.internal.com.mojang.serialization.DynamicOps;
import mods.rml.internal.com.mojang.serialization.Lifecycle;
import mods.rml.internal.com.mojang.serialization.RecordBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class DispatchedMapCodec<K, V> implements Codec<Map<K, V>> {
    private final Codec<K> keyCodec;
    private final Function<K, Codec<? extends V>> valueCodecFunction;

    public DispatchedMapCodec(Codec<K> keyCodec, Function<K, Codec<? extends V>> valueCodecFunction){
        this.keyCodec = keyCodec;
        this.valueCodecFunction = valueCodecFunction;
    }

    public Codec<K> keyCodec() {
        return keyCodec;
    }

    public Function<K, Codec<? extends V>> valueCodecFunction() {
        return valueCodecFunction;
    }

    @Override
    public <T> DataResult<T> encode(final Map<K, V> input, final DynamicOps<T> ops, final T prefix) {
        final RecordBuilder<T> mapBuilder = ops.mapBuilder();
        for (final Map.Entry<K, V> entry : input.entrySet()) {
            mapBuilder.add(keyCodec.encodeStart(ops, entry.getKey()), encodeValue(valueCodecFunction.apply(entry.getKey()), entry.getValue(), ops));
        }
        return mapBuilder.build(prefix);
    }

    @SuppressWarnings("unchecked")
    private <T, V2 extends V> DataResult<T> encodeValue(final Codec<V2> codec, final V input, final DynamicOps<T> ops) {
        return codec.encodeStart(ops, (V2) input);
    }

    @Override
    public <T> DataResult<Pair<Map<K, V>, T>> decode(final DynamicOps<T> ops, final T input) {
        return ops.getMap(input).flatMap(map -> {
            final Map<K, V> entries = new Object2ObjectArrayMap<>();
            final Stream.Builder<Pair<T, T>> failed = Stream.builder();

            final DataResult<Unit> finalResult = map.entries().reduce(
                DataResult.success(Unit.INSTANCE, Lifecycle.stable()),
                (result, entry) -> parseEntry(result, ops, entry, entries, failed),
                (r1, r2) -> r1.apply2stable((u1, u2) -> u1, r2)
            );

            final Pair<Map<K, V>, T> pair = Pair.of(ImmutableMap.copyOf(entries), input);
            final T errors = ops.createMap(failed.build());

            return finalResult.map(ignored -> pair).setPartial(pair).mapError(error -> error + " missed input: " + errors);
        });
    }

    private <T> DataResult<Unit> parseEntry(final DataResult<Unit> result, final DynamicOps<T> ops, final Pair<T, T> input, final Map<K, V> entries, final Stream.Builder<Pair<T, T>> failed) {
        final DataResult<K> keyResult = keyCodec.parse(ops, input.getFirst());
        final DataResult<V> valueResult = keyResult.map(valueCodecFunction).flatMap(valueCodec -> valueCodec.parse(ops, input.getSecond()).map(Function.identity()));
        final DataResult<Pair<K, V>> entryResult = keyResult.apply2stable(Pair::of, valueResult);

        final Optional<Pair<K, V>> entry = entryResult.resultOrPartial();
        if (entry.isPresent()) {
            final K key = entry.get().getFirst();
            final V value = entry.get().getSecond();
            if (entries.putIfAbsent(key, value) != null) {
                failed.add(input);
                return result.apply2stable((u, p) -> u, DataResult.error(() -> "Duplicate entry for key: '" + key + "'"));
            }
        }
        if (entryResult.isError()) {
            failed.add(input);
        }

        return result.apply2stable((u, p) -> u, entryResult);
    }
}
