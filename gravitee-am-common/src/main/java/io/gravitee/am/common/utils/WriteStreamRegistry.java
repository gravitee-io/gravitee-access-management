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


import io.vertx.core.streams.WriteStream;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class WriteStreamRegistry {
    final ConcurrentMap<String, WriteStream> writeStreams = new ConcurrentHashMap<>();
    final ConcurrentMap<String, AtomicInteger> refCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock getLock(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    public WriteStream getOrCreate(String streamId, Supplier<WriteStream> streamSupplier) {
        ReentrantLock lock = getLock(streamId);
        try {
            lock.lock();
            WriteStream stream = writeStreams.computeIfAbsent(streamId, id -> streamSupplier.get());
            AtomicInteger counter = refCount.computeIfAbsent(streamId, id -> new AtomicInteger(0));
            counter.incrementAndGet();
            return stream;
        } finally {
            lock.unlock();
        }
    }

    public Optional<WriteStream> decreaseUsage(String streamId) {
        ReentrantLock lock = getLock(streamId);
        try {
            lock.lock();
            AtomicInteger counter = refCount.get(streamId);
            if(counter == null || counter.get() <= 0) {
                return Optional.empty();
            }
            int value = counter.decrementAndGet();
            if(value <= 0) {
                refCount.remove(streamId);
                return Optional.ofNullable(writeStreams.remove(streamId));
            } else {
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }

    }

}
