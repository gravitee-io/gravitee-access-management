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
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class AbstractPollingRequestServiceTest {

    private static final int RETENTION = 900;
    private static final int INTERVAL = 5;

    private Client client;
    private TestPollingRequestService service;

    @Before
    public void init() {
        client = new Client();
        client.setClientId("client-id");
        service = new TestPollingRequestService();
    }

    @Test
    public void shouldFailWhenRequestIsUnknown() {
        TestObserver<TestRequest> observer = service.retrieve("unknown", client, INTERVAL).test();

        observer.assertError(InvalidGrantException.class);
    }

    @Test
    public void shouldFailWhenRequestHasExpired() {
        service.stored = pendingRequest();
        service.stored.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION - 1).toEpochMilli()));

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(ExpiredTokenException.class);
    }

    @Test
    public void shouldFailWhenRequestBelongsToAnotherClient() {
        service.stored = pendingRequest();
        service.stored.setClientId("other-client");

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(InvalidGrantException.class);
    }

    @Test
    public void shouldSlowDownWhenPolledInsideInterval() {
        service.stored = pendingRequest();
        service.stored.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL - 1).toEpochMilli()));

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(SlowDownException.class);
    }

    @Test
    public void shouldReportPendingAndRefreshLastAccessWhenIntervalElapsed() {
        service.stored = pendingRequest();
        Date previousAccess = new Date(Instant.now().minusSeconds(INTERVAL + 1).toEpochMilli());
        service.stored.setLastAccessAt(previousAccess);

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(AuthorizationPendingException.class);
        assertTrue(service.updated);
        assertTrue(service.stored.getLastAccessAt().after(previousAccess));
    }

    @Test
    public void shouldUseOverriddenIntervalHook() {
        TestPollingRequestService escalating = new TestPollingRequestService() {
            @Override
            protected int computeIntervalInSec(TestRequest request, int configuredIntervalInSec) {
                return configuredIntervalInSec * 10;
            }
        };
        escalating.stored = pendingRequest();
        escalating.stored.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL + 1).toEpochMilli()));

        TestObserver<TestRequest> observer = escalating.retrieve("id", client, INTERVAL).test();

        observer.assertError(SlowDownException.class);
    }

    @Test
    public void shouldNotTouchTheRequestOnSlowDownWhenTheHookIsNotOverridden() {
        service.stored = pendingRequest();
        service.stored.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL - 1).toEpochMilli()));

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(SlowDownException.class);
        assertFalse(service.updated);
    }

    @Test
    public void shouldRunTheSlowDownHookBeforeReportingSlowDown() {
        final boolean[] invoked = {false};
        TestPollingRequestService recording = new TestPollingRequestService() {
            @Override
            protected Completable onSlowDown(TestRequest request, int configuredIntervalInSec) {
                invoked[0] = true;
                return Completable.complete();
            }
        };
        recording.stored = pendingRequest();
        recording.stored.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL - 1).toEpochMilli()));

        TestObserver<TestRequest> observer = recording.retrieve("id", client, INTERVAL).test();

        observer.assertError(SlowDownException.class);
        assertTrue(invoked[0]);
    }

    @Test
    public void shouldDeleteAndRejectWhenDenied() {
        service.stored = pendingRequest();
        service.stored.setStatus("DENIED");

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertError(AuthorizationRejectedException.class);
        assertTrue(service.deleted);
    }

    @Test
    public void shouldDeleteAndReturnRequestWhenApproved() {
        service.stored = pendingRequest();
        service.stored.setStatus("APPROVED");

        TestObserver<TestRequest> observer = service.retrieve("id", client, INTERVAL).test();

        observer.assertValueCount(1);
        assertTrue(service.deleted);
    }

    @Test
    public void shouldRegisterWithRetentionAddedToTtl() {
        TestRequest request = new TestRequest();
        request.setId("id");
        Instant before = Instant.now();

        service.register(request, "client-id", 60).blockingGet();

        assertEquals("client-id", request.getClientId());
        assertEquals("PENDING", request.getStatus());
        assertNotNull(request.getCreatedAt());
        assertEquals(request.getCreatedAt(), request.getLastAccessAt());
        assertTrue(request.getExpireAt().toInstant().isAfter(before.plusSeconds(60 + RETENTION - 1)));
    }

    private TestRequest pendingRequest() {
        TestRequest request = new TestRequest();
        request.setId("id");
        request.setClientId(client.getClientId());
        request.setStatus("PENDING");
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL + 1).toEpochMilli()));
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION + 60).toEpochMilli()));
        return request;
    }

    private static class TestPollingRequestService extends AbstractPollingRequestService<TestRequest> {

        private TestRequest stored;
        private boolean updated;
        private boolean deleted;

        @Override
        protected Maybe<TestRequest> findRequestById(String requestId) {
            return stored == null ? Maybe.empty() : Maybe.just(stored);
        }

        @Override
        protected Single<TestRequest> createRequest(TestRequest request) {
            stored = request;
            return Single.just(request);
        }

        @Override
        protected Single<TestRequest> updateRequest(TestRequest request) {
            updated = true;
            return Single.just(request);
        }

        @Override
        protected Completable deleteRequest(String requestId) {
            deleted = true;
            return Completable.complete();
        }

        @Override
        protected String initialStatus() {
            return "PENDING";
        }

        @Override
        protected PollingRequestState stateOf(TestRequest request) {
            return PollingRequestState.valueOf(request.getStatus());
        }

        @Override
        protected int getRetentionInSec() {
            return RETENTION;
        }

        @Override
        protected String getRequestIdParameterName() {
            return "test_code";
        }
    }

    private static class TestRequest implements PollingRequest {

        private String id;
        private String status;
        private String clientId;
        private String subject;
        private Set<String> scopes;
        private Date createdAt;
        private Date lastAccessAt;
        private Date expireAt;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String getClientId() {
            return clientId;
        }

        @Override
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public String getSubject() {
            return subject;
        }

        @Override
        public void setSubject(String subject) {
            this.subject = subject;
        }

        @Override
        public Set<String> getScopes() {
            return scopes;
        }

        @Override
        public void setScopes(Set<String> scopes) {
            this.scopes = scopes;
        }

        @Override
        public Date getCreatedAt() {
            return createdAt;
        }

        @Override
        public void setCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public Date getLastAccessAt() {
            return lastAccessAt;
        }

        @Override
        public void setLastAccessAt(Date lastAccessAt) {
            this.lastAccessAt = lastAccessAt;
        }

        @Override
        public Date getExpireAt() {
            return expireAt;
        }

        @Override
        public void setExpireAt(Date expireAt) {
            this.expireAt = expireAt;
        }
    }
}
