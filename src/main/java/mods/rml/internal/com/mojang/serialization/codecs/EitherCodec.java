// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.serialization.codecs;

import mods.rml.internal.com.mojang.datafixers.util.Either;
import mods.rml.internal.com.mojang.datafixers.util.Pair;
import mods.rml.internal.com.mojang.serialization.Codec;
import mods.rml.internal.com.mojang.serialization.DataResult;
import mods.rml.internal.com.mojang.serialization.DynamicOps;

public class EitherCodec<F, S> implements Codec<Either<F, S>> {
    private final Codec<F> first;
    private final Codec<S> second;
    public EitherCodec(Codec<F> first, Codec<S> second){
        this.first = first;
        this.second = second;
    }

    public Codec<F> first() {
        return first;
    }

    public Codec<S> second() {
        return second;
    }

    @Override
    public <T> DataResult<Pair<Either<F, S>, T>> decode(final DynamicOps<T> ops, final T input) {
        final DataResult<Pair<Either<F, S>, T>> firstRead = first.decode(ops, input).map(vo -> vo.mapFirst(Either::left));
        if (firstRead.isSuccess()) {
            return firstRead;
        }
        final DataResult<Pair<Either<F, S>, T>> secondRead = second.decode(ops, input).map(vo -> vo.mapFirst(Either::right));
        if (secondRead.isSuccess()) {
            return secondRead;
        }
        if (firstRead.hasResultOrPartial()) {
            return firstRead;
        }
        if (secondRead.hasResultOrPartial()) {
            return secondRead;
        }
        return DataResult.error(() -> "Failed to parse either. First: " + firstRead.error().orElseThrow(RuntimeException::new).message() + "; Second: " + secondRead.error().orElseThrow(RuntimeException::new).message());
    }

    @Override
    public <T> DataResult<T> encode(final Either<F, S> input, final DynamicOps<T> ops, final T prefix) {
        return input.map(
            value1 -> first.encode(value1, ops, prefix),
            value2 -> second.encode(value2, ops, prefix)
        );
    }
}
