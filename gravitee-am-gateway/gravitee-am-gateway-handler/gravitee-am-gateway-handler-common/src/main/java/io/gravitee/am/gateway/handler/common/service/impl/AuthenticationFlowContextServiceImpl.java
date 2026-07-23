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
package io.gravitee.am.gateway.handler.common.service.impl;

import io.gravitee.am.gateway.handler.common.service.AuthenticationFlowContextService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.am.service.exception.AuthenticationFlowConsistencyException;
import io.gravitee.am.service.utils.RetryWithDelay;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@CustomLog
public class AuthenticationFlowContextServiceImpl implements AuthenticationFlowContextService {

    private final AuthenticationFlowContextRepository authContextRepository;
    private final int consistencyRetries;
    private final int retryDelay;
    private final int contextExpiration;
    private final boolean idempotency;

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
                log.debug("Authentication Flow Context read with version '{}' but '{}' was expected", context.getVersion(), expectedVersion);
                throw new AuthenticationFlowConsistencyException();
            }
            return context;
        }).retryWhen(RetryWithDelay.builder()
                .maxRetries(consistencyRetries)
                .initialDelay(retryDelay, TimeUnit.MILLISECONDS)
                .linear()
                .retryOn(AuthenticationFlowConsistencyException.class::isInstance)
                .build());
    }

    @Override
    public Single<AuthenticationFlowContext> removeContext(String transactionId, int expectedVersion) {
        return this.loadContext(transactionId, expectedVersion)
                .doFinally(() -> {
                    // fire and forget the deletion, in case of error the AuthenticationFLowContext TTL will finally delete the entry
                    // we don't want to stop the request processing due to deletion error if context is successfully loaded
                    clearContext(transactionId)
                            .subscribe(
                                    () -> log.debug("Deletion of Authentication Flow context '{}' succeeded after loading it", transactionId),
                                    error -> log.warn("Deletion of Authentication Flow context '{}' failed after loading it", transactionId, error));
                } );
    }

    @Override
    public Single<AuthenticationFlowContext> updateContext(AuthenticationFlowContext authContext) {
        final var now = Instant.now();
        authContext.setVersion(authContext.getVersion() + 1);
        authContext.setCreatedAt(new Date(now.toEpochMilli()));
        authContext.setExpireAt(new Date(now.plus(this.contextExpiration, ChronoUnit.SECONDS).toEpochMilli()));

        if(idempotency) {
            return authContextRepository.replace(authContext);
        } else {
            return authContextRepository.create(authContext);
        }
    }
}
