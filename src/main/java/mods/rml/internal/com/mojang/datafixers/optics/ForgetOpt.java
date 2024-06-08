// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.datafixers.optics;

import mods.rml.internal.com.mojang.datafixers.FunctionType;
import mods.rml.internal.com.mojang.datafixers.kinds.App;
import mods.rml.internal.com.mojang.datafixers.kinds.App2;
import mods.rml.internal.com.mojang.datafixers.kinds.K2;
import mods.rml.internal.com.mojang.datafixers.optics.profunctors.AffineP;
import mods.rml.internal.com.mojang.datafixers.util.Either;
import mods.rml.internal.com.mojang.datafixers.util.Pair;

import java.util.Optional;
import java.util.function.Function;

public interface ForgetOpt<R, A, B> extends App2<ForgetOpt.Mu<R>, A, B> {
    final class Mu<R> implements K2 {}

    static <R, A, B> ForgetOpt<R, A, B> unbox(final App2<Mu<R>, A, B> box) {
        return (ForgetOpt<R, A, B>) box;
    }

    Optional<R> run(final A a);

    final class Instance<R> implements AffineP<Mu<R>, Instance.Mu<R>>, App<Instance.Mu<R>, Mu<R>> {
        public static final class Mu<R> implements AffineP.Mu {}

        @Override
        public <A, B, C, D> FunctionType<App2<ForgetOpt.Mu<R>, A, B>, App2<ForgetOpt.Mu<R>, C, D>> dimap(final Function<C, A> g, final Function<B, D> h) {
            return input -> Optics.forgetOpt(c -> ForgetOpt.unbox(input).run(g.apply(c)));
        }

        @Override
        public <A, B, C> App2<ForgetOpt.Mu<R>, Pair<A, C>, Pair<B, C>> first(final App2<ForgetOpt.Mu<R>, A, B> input) {
            return Optics.forgetOpt(p -> ForgetOpt.unbox(input).run(p.getFirst()));
        }

        @Override
        public <A, B, C> App2<ForgetOpt.Mu<R>, Pair<C, A>, Pair<C, B>> second(final App2<ForgetOpt.Mu<R>, A, B> input) {
            return Optics.forgetOpt(p -> ForgetOpt.unbox(input).run(p.getSecond()));
        }

        @Override
        public <A, B, C> App2<ForgetOpt.Mu<R>, Either<A, C>, Either<B, C>> left(final App2<ForgetOpt.Mu<R>, A, B> input) {
            return Optics.forgetOpt(e -> e.left().flatMap(ForgetOpt.unbox(input)::run));
        }

        @Override
        public <A, B, C> App2<ForgetOpt.Mu<R>, Either<C, A>, Either<C, B>> right(final App2<ForgetOpt.Mu<R>, A, B> input) {
            return Optics.forgetOpt(e -> e.right().flatMap(ForgetOpt.unbox(input)::run));
        }
    }
}
