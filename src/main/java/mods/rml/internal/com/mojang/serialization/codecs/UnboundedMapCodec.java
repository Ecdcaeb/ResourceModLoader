// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.serialization.codecs;

import mods.rml.internal.com.mojang.datafixers.util.Pair;
import mods.rml.internal.com.mojang.serialization.Codec;
import mods.rml.internal.com.mojang.serialization.DataResult;
import mods.rml.internal.com.mojang.serialization.DynamicOps;
import mods.rml.internal.com.mojang.serialization.Lifecycle;

import java.util.Map;

/**
 * Key and value decoded independently, unknown set of keys
 */
public class UnboundedMapCodec<K, V> implements BaseMapCodec<K, V>, Codec<Map<K, V>> {
    private final Codec<K> keyCodec;
    private final Codec<V> elementCodec;
    public UnboundedMapCodec(Codec<K> keyCodec, Codec<V> elementCodec){
        this.keyCodec = keyCodec;
        this.elementCodec = elementCodec;
    }
    public Codec<K> keyCodec(){
        return this.keyCodec;
    };
    public final Codec<V> elementCodec(){
        return this.elementCodec;
    }

    @Override
    public <T> DataResult<Pair<Map<K, V>, T>> decode(final DynamicOps<T> ops, final T input) {
        return ops.getMap(input).setLifecycle(Lifecycle.stable()).flatMap(map -> decode(ops, map)).map(r -> Pair.of(r, input));
    }

    @Override
    public <T> DataResult<T> encode(final Map<K, V> input, final DynamicOps<T> ops, final T prefix) {
        return encode(input, ops, ops.mapBuilder()).build(prefix);
    }

    @Override
    public String toString() {
        return "UnboundedMapCodec[" + keyCodec + " -> " + elementCodec + ']';
    }
}
