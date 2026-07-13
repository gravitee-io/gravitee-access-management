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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CappedExecutorFactoryTest {

    @Test
    public void should_reject_non_positive_max_threads() {
        assertThrows(IllegalArgumentException.class, () -> CappedExecutorFactory.newCappedCachedThreadPool("test", 0));
        assertThrows(IllegalArgumentException.class, () -> CappedExecutorFactory.newCappedCachedThreadPool("test", -1));
    }

    @Test
    public void should_name_pool_threads() throws InterruptedException {
        ThreadPoolExecutor executor = CappedExecutorFactory.newCappedCachedThreadPool("my-pool", 1);
        try {
            CountDownLatch done = new CountDownLatch(1);
            final String[] threadName = new String[1];
            executor.execute(() -> {
                threadName[0] = Thread.currentThread().getName();
                done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(threadName[0].startsWith("my-pool-"), "unexpected thread name: " + threadName[0]);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void should_cap_number_of_threads_and_queue_overflow() throws InterruptedException {
        final int maxThreads = 2;
        final int submitted = 10;
        ThreadPoolExecutor executor = CappedExecutorFactory.newCappedCachedThreadPool("capped", maxThreads);
        try {
            CountDownLatch block = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(maxThreads);
            CountDownLatch completed = new CountDownLatch(submitted);
            AtomicInteger executed = new AtomicInteger();

            for (int i = 0; i < submitted; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        block.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    executed.incrementAndGet();
                    completed.countDown();
                });
            }

            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(maxThreads, executor.getPoolSize());
            assertEquals(submitted - maxThreads, executor.getQueue().size());

            block.countDown();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(submitted, executed.get());
            assertTrue(executor.getPoolSize() <= maxThreads);
        } finally {
            executor.shutdownNow();
        }
    }
}
