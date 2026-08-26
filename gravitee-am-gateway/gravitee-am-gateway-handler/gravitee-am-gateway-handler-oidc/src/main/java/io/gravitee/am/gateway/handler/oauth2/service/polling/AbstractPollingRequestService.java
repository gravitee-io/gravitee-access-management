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
package io.gravitee.am.gateway.handler.oauth2.service.polling;

import io.gravitee.am.common.polling.PollingRequest;
import io.gravitee.am.common.polling.PollingRequestState;
import io.gravitee.am.gateway.handler.oauth2.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.oauth2.exception.AuthorizationRejectedException;
import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.SlowDownException;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.util.Date;

/**
 * @author GraviteeSource Team
 */
public abstract class AbstractPollingRequestService<R extends PollingRequest> {

    protected abstract Maybe<R> findRequestById(String requestId);

    protected abstract Single<R> createRequest(R request);

    protected abstract Single<R> updateRequest(R request);

    protected abstract Completable deleteRequest(String requestId);

    protected abstract String initialStatus();

    protected abstract PollingRequestState stateOf(R request);

    protected abstract int getRetentionInSec();

    protected abstract String getRequestIdParameterName();

    /**
     * Interval, in seconds, this request is currently held to. Overridden by flows whose interval
     * widens as the client misbehaves.
     */
    protected int computeIntervalInSec(R request, int configuredIntervalInSec) {
        return configuredIntervalInSec;
    }

    /**
     * Run when a client polled inside its interval, before slow_down is returned. Overridden by
     * flows that record the offence on the request.
     */
    protected Completable onSlowDown(R request, int configuredIntervalInSec) {
        return Completable.complete();
    }

    protected boolean hasExpired(R request) {
        return (request.getExpireAt().getTime() - (getRetentionInSec() * 1000L)) < Instant.now().toEpochMilli();
    }

    protected Single<R> register(R request, String clientId, long ttlInSec) {
        final Instant now = Instant.now();
        request.setClientId(clientId);
        request.setStatus(initialStatus());
        request.setCreatedAt(new Date(now.toEpochMilli()));
        request.setLastAccessAt(new Date(now.toEpochMilli()));
        request.setExpireAt(new Date(now.plusSeconds(ttlInSec + getRetentionInSec()).toEpochMilli()));
        return createRequest(request);
    }

    protected Single<R> retrieve(String requestId, Client client, int configuredIntervalInSec) {
        return findRequestById(requestId)
                .switchIfEmpty(Single.error(() -> new InvalidGrantException(requestId)))
                .flatMap(request -> {
                    if (hasExpired(request)) {
                        return Single.error(new ExpiredTokenException());
                    }
                    if (!client.getClientId().equals(request.getClientId())) {
                        return Single.error(new InvalidGrantException(String.format("Invalid client: %s '%s' issued to client '%s' cannot be used by client '%s'",
                                getRequestIdParameterName(), requestId, request.getClientId(), client.getClientId())));
                    }
                    switch (stateOf(request)) {
                        case PENDING:
                            if (request.getLastAccessAt().toInstant().plusSeconds(computeIntervalInSec(request, configuredIntervalInSec)).isAfter(Instant.now())) {
                                return onSlowDown(request, configuredIntervalInSec).andThen(Single.<R>error(new SlowDownException()));
                            }
                            request.setLastAccessAt(new Date());
                            return updateRequest(request).flatMap(ignored -> Single.error(new AuthorizationPendingException()));
                        case DENIED:
                            return deleteRequest(requestId).toSingle(() -> {
                                throw new AuthorizationRejectedException();
                            });
                        default:
                            return deleteRequest(requestId).toSingle(() -> request);
                    }
                });
    }
}
