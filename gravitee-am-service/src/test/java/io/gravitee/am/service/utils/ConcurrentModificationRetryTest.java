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
package io.gravitee.am.service.utils;

import io.gravitee.am.service.exception.ApplicationConcurrentModificationException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.gravitee.am.service.utils.ConcurrentModificationRetry.retryOnConcurrentModification;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentModificationRetryTest {

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void shouldRetryUntilTheWriteSucceeds() {
        Supplier<Single<String>> attempt = () -> attempts.incrementAndGet() < 3
                ? Single.error(new ApplicationConcurrentModificationException("app-id"))
                : Single.just("written");

        retryOnConcurrentModification(attempt).test().assertValue("written");
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldGiveUpAfterTenAttempts() {
        Supplier<Single<String>> attempt = () -> {
            attempts.incrementAndGet();
            return Single.error(new ApplicationConcurrentModificationException("app-id"));
        };

        retryOnConcurrentModification(attempt).test().assertError(ApplicationConcurrentModificationException.class);
        assertEquals(10, attempts.get());
    }

    @Test
    void shouldNotRetryOtherErrors() {
        Supplier<Single<String>> attempt = () -> {
            attempts.incrementAndGet();
            return Single.error(new IllegalStateException("boom"));
        };

        retryOnConcurrentModification(attempt).test().assertError(IllegalStateException.class);
        assertEquals(1, attempts.get());
    }
}
