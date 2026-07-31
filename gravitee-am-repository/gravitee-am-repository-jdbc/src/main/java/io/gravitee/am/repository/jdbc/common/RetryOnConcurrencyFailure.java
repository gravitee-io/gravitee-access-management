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
package io.gravitee.am.repository.jdbc.common;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Function;
import lombok.CustomLog;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.concurrent.TimeUnit;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public class RetryOnConcurrencyFailure implements Function<Flowable<Throwable>, Publisher<?>> {
    private final String operation;
    private final int maxAttempts;
    private final long initialDelayMs;


    public RetryOnConcurrencyFailure(String operation, RetryOnConcurrencyFailureConfiguration cfg) {
        this(operation, cfg.getMaxAttempts(), cfg.getInitialDelayMs());
    }

    public RetryOnConcurrencyFailure(String operation, int maxAttempts, long initialDelayMs) {
        this.operation = operation;
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    @Override
    public Publisher<?> apply(Flowable<Throwable> errors) {
        return errors.zipWith(Flowable.range(1, maxAttempts), (error, attempt) -> {
            if (attempt >= maxAttempts || !isConcurrencyFailure(error)) {
                throw error;
            }
            long delay = initialDelayMs * attempt;
            log.warn("{} failed with {}, retrying in {} ms (attempt {}/{})",
                    operation, error.getClass().getSimpleName(), delay, attempt + 1, maxAttempts);
            return delay;
        }).flatMap(delay -> Flowable.timer(delay, TimeUnit.MILLISECONDS));
    }

    private static boolean isConcurrencyFailure(Throwable error) {
        return error instanceof ConcurrencyFailureException || error.getCause() instanceof ConcurrencyFailureException;
    }
}
