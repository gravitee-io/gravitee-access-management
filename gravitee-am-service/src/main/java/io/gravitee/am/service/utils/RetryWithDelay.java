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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import lombok.CustomLog;

@CustomLog
public class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {

    private static final int DEFAULT_MAX_RETRIES = 50;
    private static final long DEFAULT_INITIAL_DELAY = 1;
    private static final long DEFAULT_MAX_DELAY = 60;

    public enum Backoff {
        EXPONENTIAL, LINEAR
    }

    private final int maxRetries;
    private final long initialDelay;
    private final long maxDelay;
    private final TimeUnit unit;
    private final Backoff backoff;
    private final Predicate<Throwable> retryOn;

    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicLong currentDelay;

    public RetryWithDelay() {
        this(builder()
                .maxRetries(DEFAULT_MAX_RETRIES)
                .initialDelay(DEFAULT_INITIAL_DELAY, TimeUnit.SECONDS)
                .maxDelay(DEFAULT_MAX_DELAY)
                .backoff(Backoff.EXPONENTIAL));
    }

    private RetryWithDelay(Builder builder) {
        TimeUnit initialUnit = builder.unit;
        TimeUnit maxUnit = builder.maxDelayUnit != null ? builder.maxDelayUnit : builder.unit;
        TimeUnit common = initialUnit.compareTo(maxUnit) <= 0 ? initialUnit : maxUnit;

        this.maxRetries = builder.maxRetries;
        this.unit = common;
        this.initialDelay = common.convert(builder.initialDelay, initialUnit);
        this.maxDelay = builder.maxDelay <= 0 ? builder.maxDelay : common.convert(builder.maxDelay, maxUnit);
        this.backoff = builder.backoff;
        this.retryOn = builder.retryOn;
        this.currentDelay = new AtomicLong(this.initialDelay);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Publisher<?> apply(Flowable<Throwable> attempts) {
        return attempts.flatMap(error -> {
            int attempt = counter.getAndIncrement();
            boolean unlimited = maxRetries < 0;
            boolean limitExceeded = !unlimited && attempt >= maxRetries;
            if (limitExceeded || !retryOn.test(error)) {
                if (limitExceeded) {
                    log.error("Retry limit exceeded (maxRetries={})", maxRetries);
                }
                return Flowable.error(error);
            }
            long delay = nextDelay(attempt);
            if (unlimited) {
                log.warn("Retry attempt {} in {} {}", attempt + 1, delay, unit.name().toLowerCase());
            } else {
                log.warn("Retry attempt {}/{} in {} {}", attempt + 1, maxRetries, delay, unit.name().toLowerCase());
            }
            return Flowable.timer(delay, unit);
        });
    }

    long nextDelay(int attempt) {
        if (backoff == Backoff.LINEAR) {
            long delay = initialDelay * (attempt + 1L);
            return maxDelay > 0 ? Math.min(delay, maxDelay) : delay;
        }
        long delay = currentDelay.get();
        if (maxDelay <= 0 || delay < maxDelay) {
            currentDelay.set(delay * 2);
        }
        return maxDelay > 0 ? Math.min(delay, maxDelay) : delay;
    }

    public static final class Builder {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialDelay = DEFAULT_INITIAL_DELAY;
        private long maxDelay = 0;
        private TimeUnit unit = TimeUnit.SECONDS;
        private TimeUnit maxDelayUnit = null;
        private Backoff backoff = Backoff.EXPONENTIAL;
        private Predicate<Throwable> retryOn = throwable -> true;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder unlimitedRetries() {
            this.maxRetries = -1;
            return this;
        }

        public Builder initialDelay(long delay, TimeUnit unit) {
            this.initialDelay = delay;
            this.unit = unit;
            return this;
        }

        public Builder maxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            this.maxDelayUnit = null;
            return this;
        }

        public Builder maxDelay(long maxDelay, TimeUnit unit) {
            this.maxDelay = maxDelay;
            this.maxDelayUnit = unit;
            return this;
        }

        public Builder backoff(Backoff backoff) {
            this.backoff = backoff;
            return this;
        }

        public Builder linear() {
            this.backoff = Backoff.LINEAR;
            return this;
        }

        public Builder exponential() {
            this.backoff = Backoff.EXPONENTIAL;
            return this;
        }

        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        public RetryWithDelay build() {
            return new RetryWithDelay(this);
        }
    }
}
