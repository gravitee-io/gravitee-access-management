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
package io.gravitee.am.gateway.handler.common.service.polling.impl;

import io.gravitee.am.common.exception.oauth2.AuthorizationPendingException;
import io.gravitee.am.common.exception.oauth2.AuthorizationRejectedException;
import io.gravitee.am.common.exception.oauth2.ExpiredTokenException;
import io.gravitee.am.common.exception.oauth2.InvalidPollingRequestException;
import io.gravitee.am.common.exception.oauth2.SlowDownException;
import io.gravitee.am.common.polling.PollingRequest;
import io.gravitee.am.common.polling.PollingRequestState;
import io.gravitee.am.common.polling.PollingRequestStore;
import io.gravitee.am.gateway.handler.common.service.polling.PollingPolicy;
import io.gravitee.am.gateway.handler.common.service.polling.PollingService;
import io.gravitee.am.gateway.handler.common.service.polling.PollingWindow;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

public class PollingServiceImpl<R extends PollingRequest> implements PollingService<R> {

    private final PollingRequestStore<R> store;

    private final Function<R, PollingRequestState> state;

    private final int retentionInSec;

    private final PollingPolicy<R> policy;

    public PollingServiceImpl(PollingRequestStore<R> store, Function<R, PollingRequestState> state, int retentionInSec, PollingPolicy<R> policy) {
        this.store = store;
        this.state = state;
        this.retentionInSec = retentionInSec;
        this.policy = policy;
    }

    @Override
    public PollingWindow window(long ttlInSec) {
        return PollingWindow.open(ttlInSec, retentionInSec);
    }

    @Override
    public Single<R> poll(String requestId, String clientId, int intervalInSec) {
        return store.findById(requestId)
                .switchIfEmpty(Single.error(() -> new InvalidPollingRequestException(requestId)))
                .flatMap(request -> {
                    if (isExpired(request)) {
                        return Single.error(new ExpiredTokenException());
                    }
                    if (!clientId.equals(request.getClientId())) {
                        return Single.error(new InvalidPollingRequestException(String.format("Invalid client: request '%s' issued to client '%s' cannot be used by client '%s'",
                                requestId, request.getClientId(), clientId)));
                    }
                    return switch (state.apply(request)) {
                        case PENDING -> pending(request, intervalInSec);
                        case DENIED -> store.delete(requestId).ignoreElement().andThen(Single.error(AuthorizationRejectedException::new));
                        case APPROVED -> store.delete(requestId).flatMap(removed -> removed
                                ? Single.just(request)
                                : Single.error(new InvalidPollingRequestException(String.format("Request '%s' has already been redeemed", requestId))));
                    };
                });
    }

    @Override
    public boolean isExpired(R request) {
        return request.getExpireAt().toInstant().minusSeconds(retentionInSec).isBefore(Instant.now());
    }

    private Single<R> pending(R request, int intervalInSec) {
        if (request.getLastAccessAt().toInstant().plusSeconds(policy.effectiveIntervalInSec(request, intervalInSec)).isAfter(Instant.now())) {
            return store.touch(request.getId(), new Date(), true).andThen(Single.error(SlowDownException::new));
        }
        return store.touch(request.getId(), new Date(), false).andThen(Single.error(AuthorizationPendingException::new));
    }
}
