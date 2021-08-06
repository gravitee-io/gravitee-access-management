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
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PushedAuthorizationRequestRepositoryTest extends AbstractOAuthTest {

    @Autowired
    private PushedAuthorizationRequestRepository repository;

    @Test
    public void shouldNotFindById() {
        TestObserver<PushedAuthorizationRequest> observer = repository.findById("unknown-id").test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindById() {
        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        final String id = RandomString.generate();
        par.setId(id);
        par.setDomain("domain");
        par.setClient("client");
        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("key", "value");
        par.setParameters(parameters);

        repository.create(par).test().awaitTerminalEvent();

        TestObserver<PushedAuthorizationRequest> observer = repository.findById(id).test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldDelete() {
        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        final String id = RandomString.generate();
        par.setDomain("domain");
        par.setClient("client");
        par.setId(id);
        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("key", "value");
        par.setParameters(parameters);

        TestObserver<PushedAuthorizationRequest> observer = repository
                .create(par)
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
