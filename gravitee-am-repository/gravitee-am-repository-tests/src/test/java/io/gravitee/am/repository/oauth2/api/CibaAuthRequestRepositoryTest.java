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
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Set;

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

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindById() {
        CibaAuthRequest authRequest = new CibaAuthRequest();
        final String id = RandomString.generate();
        authRequest.setId(id);
        authRequest.setStatus("ONGOING");
        authRequest.setCreatedAt(new Date());
        authRequest.setLastAccessAt(new Date());
        authRequest.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        authRequest.setScopes(Set.of("openid"));
        authRequest.setSubject("subjectvalue");
        authRequest.setClientId("clientid");
        authRequest.setUserCode("code");

        repository.create(authRequest).test().awaitTerminalEvent();

        TestObserver<CibaAuthRequest> observer = repository.findById(id).test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldUpdate() {
        CibaAuthRequest authRequest = new CibaAuthRequest();
        final String id = RandomString.generate();
        authRequest.setId(id);
        authRequest.setStatus("ONGOING");
        authRequest.setCreatedAt(new Date());
        authRequest.setLastAccessAt(new Date());
        authRequest.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        authRequest.setScopes(Set.of("openid"));
        authRequest.setSubject("subjectvalue");
        authRequest.setClientId("clientid");
        authRequest.setUserCode("code");

        repository.create(authRequest).test().awaitTerminalEvent();

        TestObserver<CibaAuthRequest> observer = repository.findById(id).test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("ONGOING"));
        observer.assertNoErrors();

        authRequest.setStatus("SUCCESS");

        repository.update(authRequest).test().awaitTerminalEvent();
        observer = repository.findById(id).test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertValue(req -> req.getStatus().equals("SUCCESS"));
        observer.assertNoErrors();
    }

    @Test
    public void shouldDelete() {
        CibaAuthRequest authRequest = new CibaAuthRequest();
        final String id = RandomString.generate();
        authRequest.setId(id);
        authRequest.setStatus("ONGOING");
        authRequest.setCreatedAt(new Date());
        authRequest.setLastAccessAt(new Date());
        authRequest.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        authRequest.setScopes(Set.of("openid"));
        authRequest.setSubject("subjectvalue");
        authRequest.setClientId("clientid");
        authRequest.setUserCode("code");


        TestObserver<CibaAuthRequest> observer = repository
                .create(authRequest)
                .ignoreElement()
                .andThen(repository.findById(id))
                .ignoreElement()
                .andThen(repository.delete(id))
                .andThen(repository.findById(id))
                .test();

        observer.awaitTerminalEvent();
        observer.assertNoValues();
        observer.assertNoErrors();
    }


}
