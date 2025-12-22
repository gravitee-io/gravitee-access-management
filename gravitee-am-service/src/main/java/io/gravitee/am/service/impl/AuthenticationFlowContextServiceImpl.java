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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.AuthenticationFlowConsistencyException;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuthenticationFlowContextServiceImpl implements AuthenticationFlowContextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFlowContextServiceImpl.class);

    @Lazy
    @Autowired
    private AuthenticationFlowContextRepository authContextRepository;

    @Value("${authenticationFlow.maxRetries:2}")
    private int consistencyRetries;

    @Value("${authenticationFlow.retryInterval:1000}")
    private int retryDelay;

    @Value("${authenticationFlow.expirationTimeOut:300}")
    private int contextExpiration;

    @Override
    public Completable clearContext(final String transactionId) {
        if (transactionId == null) {
            return Completable.complete();
        }
        return authContextRepository.delete(transactionId);
    }

    @Override
    public Single<AuthenticationFlowContext> loadContext(String transactionId, int expectedVersion) {
        return authContextRepository.findLastByTransactionId(transactionId).switchIfEmpty(Single.fromCallable(() -> {
            AuthenticationFlowContext context = new AuthenticationFlowContext();
            context.setTransactionId(transactionId);
            context.setVersion(0);
            context.setCreatedAt(new Date());
            return context;
        })).map(context -> {
            if (context.getVersion() > 0 && context.getVersion() < expectedVersion) {
                LOGGER.debug("Authentication Flow Context read with version '{}' but '{}' was expected", context.getVersion(), expectedVersion);
                throw new AuthenticationFlowConsistencyException();
            }
            return context;
        }).retryWhen(new RetryWithDelay(consistencyRetries, retryDelay));
    }

    @Override
    public Single<AuthenticationFlowContext> removeContext(String transactionId, int expectedVersion) {
        return this.loadContext(transactionId, expectedVersion)
                .doFinally(() -> {
                    // fire and forget the deletion, in case of error the AuthenticationFLowContext TTL will finally delete the entry
                    // we don't want to stop the request processing due to deletion error if context is successfully loaded
                    clearContext(transactionId)
                            .subscribe(
                                    () -> LOGGER.debug("Deletion of Authentication Flow context '{}' succeeded after loading it", transactionId),
                                    error -> LOGGER.warn("Deletion of Authentication Flow context '{}' failed after loading it", transactionId, error));
                } );
    }

    @Override
    public Single<AuthenticationFlowContext> updateContext(AuthenticationFlowContext authContext) {
        final var now = Instant.now();
        authContext.setVersion(authContext.getVersion() + 1);
        authContext.setCreatedAt(new Date(now.toEpochMilli()));
        authContext.setExpireAt(new Date(now.plus(this.contextExpiration, ChronoUnit.SECONDS).toEpochMilli()));

        return authContextRepository.create(authContext);
    }

    /**
     * Retry load context with delay
     * from : https://stackoverflow.com/a/25292833
     */
    private class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
        private final int maxRetries;
        private final int retryDelayMillis;
        private int retryCount;

        public RetryWithDelay(int retries, int delay) {
            this.maxRetries = retries;
            this.retryDelayMillis = delay;
            this.retryCount = 0;
        }

        @Override
        public Publisher<?> apply(@NonNull Flowable<Throwable> attempts) {
            return attempts
                    .flatMap((throwable) -> {
                        // perform retry only on Consistency exception
                        if (throwable instanceof AuthenticationFlowConsistencyException && ++retryCount < maxRetries) {
                                // When this Observable calls onNext, the original
                                // Observable will be retried (i.e. re-subscribed).
                                return Flowable.timer((long) retryDelayMillis * (retryCount + 1),
                                        TimeUnit.MILLISECONDS);
                            }

                        // Max retries hit. Just pass the error along.
                        return Flowable.error(throwable);
                    });
        }
    }
}
