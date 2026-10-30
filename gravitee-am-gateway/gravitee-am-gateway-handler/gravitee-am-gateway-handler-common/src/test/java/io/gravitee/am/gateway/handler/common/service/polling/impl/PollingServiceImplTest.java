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
import io.gravitee.am.gateway.handler.common.service.polling.PollingWindow;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class PollingServiceImplTest {

    private static final String CLIENT_ID = "client-id";
    private static final int INTERVAL = 5;
    private static final int RETENTION = 900;

    private InMemoryStore store;

    @BeforeEach
    void init() {
        store = new InMemoryStore();
    }

    @Test
    void shouldRejectUnknownRequest() {
        service(new KeepIntervalPollingPolicy<>()).poll("unknown", CLIENT_ID, INTERVAL).test()
                .assertError(InvalidPollingRequestException.class);
    }

    @Test
    void shouldReportExpiredRequest() {
        store.save(pendingRequest()).expireAt = Date.from(Instant.now().plusSeconds(RETENTION - 1));

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(ExpiredTokenException.class);
    }

    @Test
    void shouldRejectRequestIssuedToAnotherClient() {
        store.save(pendingRequest()).clientId = "other-client";

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(e -> e instanceof InvalidPollingRequestException
                        && e.getMessage().equals("Invalid client: request 'id' issued to client 'other-client' cannot be used by client 'client-id'"));
    }

    @Test
    void shouldReportPendingAndTouchRequestOnceIntervalElapsed() {
        TestRequest request = store.save(pendingRequest());
        Date previousAccess = request.lastAccessAt;

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(AuthorizationPendingException.class);

        assertThat(store.touches).isEqualTo(1);
        assertThat(request.slowDownCount).isZero();
        assertThat(request.lastAccessAt).isAfter(previousAccess);
    }

    @Test
    void shouldTouchAndCountSlowDown() {
        TestRequest request = store.save(pendingRequest());
        Date previousAccess = Date.from(Instant.now().minusSeconds(INTERVAL - 1));
        request.lastAccessAt = previousAccess;

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(SlowDownException.class);

        assertThat(store.touches).isEqualTo(1);
        assertThat(request.slowDownCount).isEqualTo(1);
        assertThat(request.lastAccessAt).isAfter(previousAccess);
    }

    @Test
    void shouldHoldClientToWidenedInterval() {
        TestRequest request = store.save(pendingRequest());
        request.slowDownCount = 1;
        request.lastAccessAt = Date.from(Instant.now().minusSeconds(INTERVAL + 1));

        service(new WidenIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(SlowDownException.class);

        assertThat(request.slowDownCount).isEqualTo(2);
    }

    @Test
    void shouldCapWidenedIntervalAtMax() {
        TestRequest request = store.save(pendingRequest());
        request.slowDownCount = 100;
        request.lastAccessAt = Date.from(Instant.now().minusSeconds(WidenIntervalPollingPolicy.MAX_INTERVAL_IN_SEC + 1));

        service(new WidenIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(AuthorizationPendingException.class);
    }

    @Test
    void shouldIgnoreSlowDownCountWhenKeepingInterval() {
        store.save(pendingRequest()).slowDownCount = 1;

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(AuthorizationPendingException.class);
    }

    @Test
    void shouldDeleteAndRejectDeniedRequest() {
        store.save(pendingRequest()).state = PollingRequestState.DENIED;

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(AuthorizationRejectedException.class);

        assertThat(store.request).isNull();
    }

    @Test
    void shouldDeleteAndReturnApprovedRequest() {
        TestRequest request = store.save(pendingRequest());
        request.state = PollingRequestState.APPROVED;

        service(new KeepIntervalPollingPolicy<>()).poll("id", CLIENT_ID, INTERVAL).test()
                .assertValue(request);

        assertThat(store.request).isNull();
    }

    @Test
    void shouldRedeemApprovedRequestOnlyOnceWhenPollsRace() {
        TestRequest request = store.save(pendingRequest());
        request.state = PollingRequestState.APPROVED;
        PollingServiceImpl<TestRequest> service = service(new KeepIntervalPollingPolicy<>());
        store.findOverride = Maybe.just(request);

        service.poll("id", CLIENT_ID, INTERVAL).test().assertValue(request);
        service.poll("id", CLIENT_ID, INTERVAL).test()
                .assertError(e -> e instanceof InvalidPollingRequestException
                        && e.getMessage().equals("Request 'id' has already been redeemed"));
    }

    @Test
    void shouldConsiderRequestExpiredOnceRetentionIsAllThatRemains() {
        PollingServiceImpl<TestRequest> service = service(new KeepIntervalPollingPolicy<>());
        TestRequest request = new TestRequest();
        request.expireAt = Date.from(Instant.now().plusSeconds(RETENTION - 1));

        assertThat(service.isExpired(request)).isTrue();

        request.expireAt = Date.from(Instant.now().plusSeconds(RETENTION + 60));

        assertThat(service.isExpired(request)).isFalse();
    }

    @Test
    void shouldOpenWindowWithItsRetention() {
        PollingWindow window = service(new KeepIntervalPollingPolicy<>()).window(60);

        assertThat(window.expireAt().toInstant()).isEqualTo(window.createdAt().toInstant().plusSeconds(60 + RETENTION));
    }

    private PollingServiceImpl<TestRequest> service(PollingPolicy<TestRequest> policy) {
        return new PollingServiceImpl<>(store, request -> request.state, RETENTION, policy);
    }

    private TestRequest pendingRequest() {
        TestRequest request = new TestRequest();
        request.clientId = CLIENT_ID;
        request.state = PollingRequestState.PENDING;
        request.lastAccessAt = Date.from(Instant.now().minusSeconds(INTERVAL + 1));
        request.expireAt = Date.from(Instant.now().plusSeconds(RETENTION + 60));
        return request;
    }

    private static class InMemoryStore implements PollingRequestStore<TestRequest> {

        private TestRequest request;
        private Maybe<TestRequest> findOverride;
        private int touches;

        TestRequest save(TestRequest request) {
            this.request = request;
            return request;
        }

        @Override
        public Maybe<TestRequest> findById(String id) {
            if (findOverride != null) {
                return findOverride;
            }
            return request != null && request.getId().equals(id) ? Maybe.just(request) : Maybe.empty();
        }

        @Override
        public Completable touch(String id, Date lastAccessAt, boolean slowDown) {
            return Completable.fromAction(() -> {
                touches++;
                request.lastAccessAt = lastAccessAt;
                if (slowDown) {
                    request.slowDownCount++;
                }
            });
        }

        @Override
        public Single<Boolean> delete(String id) {
            return Single.fromCallable(() -> {
                boolean removed = request != null;
                request = null;
                return removed;
            });
        }
    }

    private static class TestRequest implements PollingRequest {

        private PollingRequestState state;
        private String clientId;
        private Date createdAt;
        private Date lastAccessAt;
        private Date expireAt;
        private int slowDownCount;

        @Override
        public String getId() {
            return "id";
        }

        @Override
        public String getStatus() {
            return state.name();
        }

        @Override
        public String getClientId() {
            return clientId;
        }

        @Override
        public Date getCreatedAt() {
            return createdAt;
        }

        @Override
        public Date getLastAccessAt() {
            return lastAccessAt;
        }

        @Override
        public Date getExpireAt() {
            return expireAt;
        }

        @Override
        public int getSlowDownCount() {
            return slowDownCount;
        }
    }
}
