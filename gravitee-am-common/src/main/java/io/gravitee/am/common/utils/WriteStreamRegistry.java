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

/**
 * Ref-counted registry of connection-scoped resources shared by reporter instances that point at the
 * same endpoint, so N reporters configured against one backend open one connection rather than N.
 * <p>
 * Most entries are Vert.x {@link WriteStream}s, which is what the class is named after, but a shared
 * resource is not always a write stream — the Elasticsearch reporter shares an HTTP client that is
 * not one — so entries are held untyped and the typed accessors below cast on the way out. Keys are
 * expected to be namespaced by the sharing reporter and to hash every setting that identifies the
 * connection, so a reconfigured reporter gets a new resource rather than the previous one.
 */
public class WriteStreamRegistry {
    final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();
    final ConcurrentMap<String, AtomicInteger> refCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock getLock(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    public WriteStream getOrCreate(String streamId, Supplier<WriteStream> streamSupplier) {
        return share(streamId, streamSupplier::get);
    }

    public Optional<WriteStream> decreaseUsage(String streamId) {
        return release(streamId);
    }

    /**
     * Returns the resource registered under {@code id}, creating it from {@code supplier} the first
     * time, and records one more user of it.
     */
    @SuppressWarnings("unchecked")
    public <T> T share(String id, Supplier<T> supplier) {
        ReentrantLock lock = getLock(id);
        try {
            lock.lock();
            Object resource = resources.computeIfAbsent(id, key -> supplier.get());
            AtomicInteger counter = refCount.computeIfAbsent(id, key -> new AtomicInteger(0));
            counter.incrementAndGet();
            return (T) resource;
        } finally {
            lock.unlock();
        }
    }

    /** How many distinct resources are currently shared, which is how many connections are open. */
    public int size() {
        return resources.size();
    }

    /**
     * Records one fewer user of {@code id}. Returns the resource only when the last user let go, so
     * the caller closes it exactly once; an empty result means somebody else is still using it.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> release(String id) {
        ReentrantLock lock = getLock(id);
        try {
            lock.lock();
            AtomicInteger counter = refCount.get(id);
            if(counter == null || counter.get() <= 0) {
                return Optional.empty();
            }
            int value = counter.decrementAndGet();
            if(value <= 0) {
                refCount.remove(id);
                return Optional.ofNullable((T) resources.remove(id));
            } else {
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }

    }

}
