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
package io.gravitee.am.service.secrets.kryo;

import com.esotericsoftware.kryo.Kryo;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author GraviteeSource Team
 */
public final class KryoPool {

    final BlockingQueue<Kryo> queue;
    final Supplier<Kryo> factory;
    final Duration borrowTimeout;

    public KryoPool(int maxSize,
                    Duration borrowTimeout,
                    Supplier<Kryo> factory) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(maxSize);
        this.factory = Objects.requireNonNull(factory, "factory");
        this.borrowTimeout = Objects.requireNonNull(borrowTimeout, "borrowTimeout");
    }

    private Kryo borrow() throws InterruptedException {
        Kryo kryo = queue.poll(borrowTimeout.toMillis(), TimeUnit.MILLISECONDS);
        return (kryo != null) ? kryo : factory.get();
    }

    private void release(Kryo kryo) {
        if (kryo == null) {
            return;
        }
        queue.offer(kryo);
    }

    public <T> T withKryo(Function<Kryo, T> fn) {
        Kryo kryo = null;
        try {
            kryo = borrow();
            return fn.apply(kryo);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while borrowing Kryo", ie);
        } finally {
            release(kryo);
        }
    }

    public void doWithKryo(Consumer<Kryo> fn) {
        withKryo(k -> { fn.accept(k); return null; });
    }
}