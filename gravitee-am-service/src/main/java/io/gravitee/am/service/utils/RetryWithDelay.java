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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryWithDelay.class);

    private final AtomicInteger delayInSec = new AtomicInteger(1);
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Publisher<?> apply(Flowable<Throwable> throwableFlowable) {
        return throwableFlowable.flatMap(err -> {
            if (counter.getAndIncrement() < 50) {
                int delay = delayInSec.get();
                LOGGER.warn("Initialization failed, attempt={}/50, delay={}", counter.get(), delay);
                if (delay < 60) {
                    delayInSec.set(delay * 2);
                }
                return Flowable.timer(delay, TimeUnit.SECONDS);
            } else {
                LOGGER.error("Retry limit exceeded");
                return Flowable.error(err);
            }
        });
    }
}