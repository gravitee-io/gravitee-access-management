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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaAuthRequestRepositoryTest extends AbstractOAuthTest {

    @Autowired
    private CibaAuthRequestRepository repository;

    @Test
    public void shouldNotFindById() {
        TestObserver<CibaAuthRequest> observer = repository.findById("unknown-id").test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindById() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);

        repository.create(authRequest).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<CibaAuthRequest> observer = repository.findById(id).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindByExternalId() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);

        repository.create(authRequest).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<CibaAuthRequest> observer = repository.findByExternalId(authRequest.getExternalTrxId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdate() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);

        repository.create(authRequest).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<CibaAuthRequest> observer = repository.findById(id).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("ONGOING"));
        observer.assertNoErrors();

        authRequest.setStatus("SUCCESS");
        authRequest.setDeviceNotifierId("notifierId");

        repository.update(authRequest).test().awaitDone(10, TimeUnit.SECONDS);
        observer = repository.findById(id).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("SUCCESS") && req.getDeviceNotifierId().equals("notifierId"));
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdateStatus() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);

        repository.create(authRequest).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<CibaAuthRequest> observer = repository.findById(id).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("ONGOING"));
        observer.assertNoErrors();

        repository.updateStatus(authRequest.getId(), "SUCCESS").test().awaitDone(10, TimeUnit.SECONDS);
        observer = repository.findById(id).test();

        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("SUCCESS"));
        observer.assertNoErrors();
    }

    private CibaAuthRequest buildCibaAuthRequest(String id) {
        CibaAuthRequest authRequest = new CibaAuthRequest();
        authRequest.setId(id);
        authRequest.setStatus("ONGOING");
        authRequest.setCreatedAt(new Date());
        authRequest.setLastAccessAt(new Date());
        authRequest.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        authRequest.setScopes(Set.of("openid"));
        authRequest.setSubject("subjectvalue");
        authRequest.setClientId("clientid");
        authRequest.setDeviceNotifierId("notifierid");
        authRequest.setExternalTrxId("adtrxid"+id);
        authRequest.setExternalInformation(Map.of("key1", "value1", "key2", Arrays.asList("a", "b")));
        return authRequest;
    }

    @Test
    public void shouldDelete() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);

        TestObserver<CibaAuthRequest> observer = repository
                .create(authRequest)
                .ignoreElement()
                .andThen(repository.findById(id))
                .ignoreElement()
                .andThen(repository.delete(id))
                .andThen(repository.findById(id))
                .test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldCreateWithLongClientName() {
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);
        authRequest.setClientId("very-long-client-very-long-client-very-long-client-very-long-client-very-long-client-very-long-client");

        TestObserver<CibaAuthRequest> observer = repository.create(authRequest).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void shouldPersistAndReadAuthorizationDetails() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setId("ciba-rar-1");
        request.setClientId("client-1");
        request.setSubject("alice");
        request.setStatus("ONGOING");
        request.setScopes(Set.of("openid"));
        request.setCreatedAt(new Date());
        request.setLastAccessAt(new Date());
        request.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        request.setExternalTrxId("ciba-rar-trx-1");

        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "fdx_v1.0");
        detail.put("scope", List.of("ACCOUNT_DETAILED"));
        request.setAuthorizationDetails(List.of(detail));

        CibaAuthRequest created = repository.create(request).blockingGet();
        assertNotNull(created.getAuthorizationDetails());

        CibaAuthRequest found = repository.findById("ciba-rar-1").blockingGet();
        assertNotNull(found.getAuthorizationDetails());
        assertEquals(1, found.getAuthorizationDetails().size());
        assertEquals("fdx_v1.0", found.getAuthorizationDetails().get(0).get("type"));
        assertEquals(List.of("ACCOUNT_DETAILED"), found.getAuthorizationDetails().get(0).get("scope"));
    }

    @Test
    public void shouldCreateWithNullSubject() {
        // CIBA federation persists the request at bc-authorize, before the subject is
        // resolved (identity is asserted downstream at device approval). The subject is
        // therefore null during the ONGOING window and the store must accept it.
        final String id = RandomString.generate();
        CibaAuthRequest authRequest = buildCibaAuthRequest(id);
        authRequest.setSubject(null);

        TestObserver<CibaAuthRequest> createObserver = repository.create(authRequest).test();
        createObserver.awaitDone(10, TimeUnit.SECONDS);
        createObserver.assertComplete();
        createObserver.assertNoErrors();

        TestObserver<CibaAuthRequest> findObserver = repository.findById(id).test();
        findObserver.awaitDone(10, TimeUnit.SECONDS);
        findObserver.assertComplete();
        findObserver.assertValueCount(1);
        findObserver.assertNoErrors();
        findObserver.assertValue(req -> req.getSubject() == null);
    }

}
