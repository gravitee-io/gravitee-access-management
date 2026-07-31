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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * @author GraviteeSource Team
 */
public class RetryOnConcurrencyFailureTest {

    private static final int MAX_ATTEMPTS = 3;

    private RetryOnConcurrencyFailure retry() {
        return new RetryOnConcurrencyFailure("test", MAX_ATTEMPTS, 1);
    }

    @Test
    public void shouldRetryUntilSuccess() {
        AtomicInteger executions = new AtomicInteger();
        TestObserver<Void> observer = Completable.defer(() -> executions.incrementAndGet() == 1
                        ? Completable.error(new CannotAcquireLockException("deadlock"))
                        : Completable.complete())
                .retryWhen(retry())
                .test();

        observer.awaitDone(5, TimeUnit.SECONDS).assertComplete();
        assertEquals(2, executions.get());
    }

    @Test
    public void shouldFailWithOriginalErrorWhenAttemptsExhausted() {
        AtomicInteger executions = new AtomicInteger();
        TestObserver<Void> observer = Completable.defer(() -> {
                    executions.incrementAndGet();
                    return Completable.error(new CannotAcquireLockException("deadlock"));
                })
                .retryWhen(retry())
                .test();

        observer.awaitDone(5, TimeUnit.SECONDS).assertError(CannotAcquireLockException.class);
        assertEquals(MAX_ATTEMPTS, executions.get());
    }

    @Test
    public void shouldNotRetryOnNonConcurrencyFailure() {
        AtomicInteger executions = new AtomicInteger();
        TestObserver<Void> observer = Completable.defer(() -> {
                    executions.incrementAndGet();
                    return Completable.error(new DuplicateKeyException("duplicate"));
                })
                .retryWhen(retry())
                .test();

        observer.awaitDone(5, TimeUnit.SECONDS).assertError(DuplicateKeyException.class);
        assertEquals(1, executions.get());
    }

    @Test
    public void shouldRetryWhenConcurrencyFailureIsWrapped() {
        AtomicInteger executions = new AtomicInteger();
        TestObserver<Void> observer = Completable.defer(() -> executions.incrementAndGet() == 1
                        ? Completable.error(new IllegalStateException("wrapped", new CannotAcquireLockException("deadlock")))
                        : Completable.complete())
                .retryWhen(retry())
                .test();

        observer.awaitDone(5, TimeUnit.SECONDS).assertComplete();
        assertEquals(2, executions.get());
    }

    @Test
    public void shouldRestartAttemptCountOnEachSubscription() {
        AtomicInteger executions = new AtomicInteger();
        Completable action = Completable.defer(() -> {
                    executions.incrementAndGet();
                    return Completable.error(new CannotAcquireLockException("deadlock"));
                })
                .retryWhen(retry());

        action.test().awaitDone(5, TimeUnit.SECONDS).assertError(CannotAcquireLockException.class);
        action.test().awaitDone(5, TimeUnit.SECONDS).assertError(CannotAcquireLockException.class);

        assertEquals(MAX_ATTEMPTS * 2, executions.get());
    }
}
