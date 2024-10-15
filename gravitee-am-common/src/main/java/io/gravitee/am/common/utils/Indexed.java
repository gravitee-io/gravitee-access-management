/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.common.utils;

import io.reactivex.rxjava3.core.Flowable;

import java.util.stream.Stream;

/**
 * A wrapper class for working with ordered streams of values where the position of an item in the source matters.
 */
public record Indexed<T>(int index, T value) {
    public static <R> Indexed<R> of(R value, int index) {
        return new Indexed<>(index, value);
    }

    /**
     * Transforms an iterable: [a,b,c] into a flowable [(0,a), (1,b), (2, c)]
     */
    public static <R> Flowable<Indexed<R>> toIndexedFlowable(Iterable<R> source) {
        return Flowable.fromIterable(source).zipWith(Flowable.fromStream(Stream.iterate(0, i -> i + 1)), Indexed::of);
    }
}
