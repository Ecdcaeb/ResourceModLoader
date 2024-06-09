// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.serialization.codecs;

import com.google.common.collect.Lists;
import mods.rml.internal.com.mojang.datafixers.util.Pair;
import mods.rml.internal.com.mojang.datafixers.util.Unit;
import mods.rml.internal.com.mojang.serialization.Codec;
import mods.rml.internal.com.mojang.serialization.DataResult;
import mods.rml.internal.com.mojang.serialization.DynamicOps;
import mods.rml.internal.com.mojang.serialization.Lifecycle;
import mods.rml.internal.com.mojang.serialization.ListBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ListCodec<E> implements Codec<List<E>> {
    private final Codec<E> elementCodec;
    private final int minSize;
    private final int maxSize;
    public ListCodec(Codec<E> elementCodec, int minSize, int maxSize){
        this.elementCodec = elementCodec;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    public Codec<E> elementCodec() {
        return elementCodec;
    }

    public int maxSize() {
        return maxSize;
    }

    public int minSize() {
        return minSize;
    }

    private <R> DataResult<R> createTooShortError(final int size) {
        return DataResult.error(() -> "List is too short: " + size + ", expected range [" + minSize + "-" + maxSize + "]");
    }

    private <R> DataResult<R> createTooLongError(final int size) {
        return DataResult.error(() -> "List is too long: " + size + ", expected range [" + minSize + "-" + maxSize + "]");
    }

    @Override
    public <T> DataResult<T> encode(final List<E> input, final DynamicOps<T> ops, final T prefix) {
        if (input.size() < minSize) {
            return createTooShortError(input.size());
        }
        if (input.size() > maxSize) {
            return createTooLongError(input.size());
        }
        final ListBuilder<T> builder = ops.listBuilder();
        for (final E element : input) {
            builder.add(elementCodec.encodeStart(ops, element));
        }
        return builder.build(prefix);
    }

    @Override
    public <T> DataResult<Pair<List<E>, T>> decode(final DynamicOps<T> ops, final T input) {
        return ops.getList(input).setLifecycle(Lifecycle.stable()).flatMap(stream -> {
            final DecoderState<T> decoder = new DecoderState<>(ops);
            stream.accept(decoder::accept);
            return decoder.build();
        });
    }

    @Override
    public String toString() {
        return "ListCodec[" + elementCodec + ']';
    }

    private static final DataResult<Unit> INITIAL_RESULT = DataResult.success(Unit.INSTANCE, Lifecycle.stable());

    private class DecoderState<T> {
        private final DynamicOps<T> ops;
        private final List<E> elements = new ArrayList<>();
        private final Stream.Builder<T> failed = Stream.builder();
        private DataResult<Unit> result = ListCodec.INITIAL_RESULT;
        private int totalCount;

        private DecoderState(final DynamicOps<T> ops) {
            this.ops = ops;
        }

        public void accept(final T value) {
            totalCount++;
            if (elements.size() >= ListCodec.this.maxSize) {
                failed.add(value);
                return;
            }
            final DataResult<Pair<E, T>> elementResult = ListCodec.this.elementCodec.decode(ops, value);
            elementResult.error().ifPresent(error -> failed.add(value));
            elementResult.resultOrPartial().ifPresent(pair -> elements.add(pair.getFirst()));
            result = result.apply2stable((result, element) -> result, elementResult);
        }

        public DataResult<Pair<List<E>, T>> build() {
            if (elements.size() < ListCodec.this.minSize) {
                return createTooShortError(elements.size());
            }
            final T errors = ops.createList(failed.build());
            final Pair<List<E>, T> pair = Pair.of(Lists.newArrayList(elements), errors);
            if (totalCount > ListCodec.this.maxSize) {
                result = createTooLongError(totalCount);
            }
            return result.map(ignored -> pair).setPartial(pair);
        }
    }
}
