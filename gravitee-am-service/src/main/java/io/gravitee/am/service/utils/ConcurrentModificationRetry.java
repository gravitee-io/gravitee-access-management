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
import lombok.CustomLog;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public final class ConcurrentModificationRetry {

    /**
     * Every conflict means another write succeeded, so k concurrent writers need at most k attempts.
     */
    static final int MAX_ATTEMPTS = 10;

    private ConcurrentModificationRetry() {
    }

    /**
     * Runs a new {@code attempt} (which must re-read what it writes) while it fails with a concurrent modification.
     * Retries are immediate; once {@link #MAX_ATTEMPTS} attempts failed, the last conflict propagates.
     */
    public static <T> Single<T> retryOnConcurrentModification(Supplier<Single<T>> attempt) {
        return Single.defer(attempt)
                .retry(MAX_ATTEMPTS - 1, ApplicationConcurrentModificationException.class::isInstance)
                .doOnError(ex -> {
                    if (ex instanceof ApplicationConcurrentModificationException conflict) {
                        log.warn("Concurrent modification retries exhausted applicationId={} attempts={}", conflict.getApplicationId(), MAX_ATTEMPTS);
                    }
                });
    }
}
