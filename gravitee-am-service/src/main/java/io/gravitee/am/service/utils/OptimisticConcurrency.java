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

import io.gravitee.am.service.exception.ResourceConcurrentModificationException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import lombok.CustomLog;

import java.util.Date;

/**
 * Optimistic concurrency on a resource whose {@code updatedAt} is the concurrency token: a conditional write only
 * happens if the stored {@code updatedAt} is still the one the writer read.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public final class OptimisticConcurrency {

    /**
     * Every conflict means another write succeeded, so k concurrent writers need at most k attempts.
     */
    static final int MAX_ATTEMPTS = 10;

    private OptimisticConcurrency() {
    }

    /**
     * The {@code updatedAt} of a write that replaces a resource read with {@code expectedUpdatedAt}.
     * The token must change on every write: two writes in the same millisecond (or a node whose clock is behind)
     * would otherwise store the token the other writer still expects.
     */
    public static Date nextUpdatedAt(Date expectedUpdatedAt) {
        Date now = new Date();
        if (expectedUpdatedAt == null) {
            return now;
        }
        return new Date(Math.max(now.getTime(), expectedUpdatedAt.getTime() + 1));
    }

    /**
     * Whether {@code throwable} is a concurrent modification: nothing was written, and the write is retried.
     */
    public static boolean isConcurrentModification(Throwable throwable) {
        return throwable instanceof ResourceConcurrentModificationException;
    }

    /**
     * Runs a new {@code attempt} (which must re-read what it writes) while it fails with a concurrent modification.
     * Retries are immediate; once {@link #MAX_ATTEMPTS} attempts failed, the last conflict propagates.
     */
    public static <T> Single<T> retryOnConcurrentModification(Supplier<Single<T>> attempt) {
        return Single.defer(attempt)
                .retry(MAX_ATTEMPTS - 1, OptimisticConcurrency::isConcurrentModification)
                .doOnError(ex -> {
                    if (ex instanceof ResourceConcurrentModificationException conflict) {
                        log.warn("Concurrent modification retries exhausted resourceType={} id={} attempts={}", conflict.getResourceType(), conflict.getId(), MAX_ATTEMPTS);
                    }
                });
    }
}
