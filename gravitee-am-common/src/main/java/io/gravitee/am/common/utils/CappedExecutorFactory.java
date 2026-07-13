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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for {@link ThreadPoolExecutor}s that behave like {@link java.util.concurrent.Executors#newCachedThreadPool()}
 * (threads created on demand, idle threads reclaimed) but with a hard cap on the maximum number of threads.
 * <p>
 * A plain cached thread pool has no upper bound on the number of threads it creates: under a burst of
 * concurrent tasks it will keep spawning threads, which can exhaust memory / file descriptors. The pool
 * returned here caps the number of threads to {@code maxThreads}; any tasks submitted while all threads are
 * busy are queued instead of triggering the creation of a new thread.
 * <p>
 * This factory is intentionally free of any metrics/monitoring concern. To also export the pool state as
 * metrics, bind the returned executor to a registry (see {@code io.gravitee.am.monitoring.metrics}).
 *
 * @author GraviteeSource Team
 */
public final class CappedExecutorFactory {

    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;

    private CappedExecutorFactory() {
    }

    /**
     * Creates a cached-like thread pool capped at {@code maxThreads} threads, using the default 60s
     * keep-alive for idle threads.
     *
     * @param name       prefix used to name the pool threads (for observability in thread dumps)
     * @param maxThreads the maximum number of threads the pool is allowed to create (must be &gt; 0)
     */
    public static ThreadPoolExecutor newCappedCachedThreadPool(String name, int maxThreads) {
        return newCappedCachedThreadPool(name, maxThreads, DEFAULT_KEEP_ALIVE_SECONDS);
    }

    /**
     * Creates a cached-like thread pool capped at {@code maxThreads} threads.
     *
     * @param name             prefix used to name the pool threads (for observability in thread dumps)
     * @param maxThreads       the maximum number of threads the pool is allowed to create (must be &gt; 0)
     * @param keepAliveSeconds how long an idle thread is kept alive before being reclaimed
     */
    private static ThreadPoolExecutor newCappedCachedThreadPool(String name, int maxThreads, long keepAliveSeconds) {
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("maxThreads must be greater than 0, was " + maxThreads);
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedThreadFactory(name));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ThreadFactory namedThreadFactory(String name) {
        final AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(name + "-" + counter.incrementAndGet());
            return thread;
        };
    }
}
