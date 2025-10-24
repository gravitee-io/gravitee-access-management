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

import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeRepositoryTest extends AbstractOAuthTest {

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Test
    public void shouldRetrieveAndRemoveAuthorizationCode() {
        String code = "testCode123213213";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(code);
        authorizationCode.setClientId("clientId");
        authorizationCode.setContextVersion(1);

        authorizationCodeRepository.create(authorizationCode).blockingGet();

        authorizationCodeRepository.findAndRemoveByCodeAndClientId(code, "clientId")
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .assertValue(authorizationCode1 -> authorizationCode1.getCode().equals(code) && authorizationCode1.getContextVersion() == 1);

        authorizationCodeRepository.findAndRemoveByCodeAndClientId(code, "clientId")
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .assertNoValues();
    }

    @Test
    public void shouldStoreCode() {
        String code = "testCode";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(code);
        authorizationCode.setContextVersion(1);

        authorizationCodeRepository.create(authorizationCode).blockingGet();

        TestObserver<AuthorizationCode> testObserver = authorizationCodeRepository.findByCode(code).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(authorizationCode1 -> authorizationCode1.getCode().equals(code)
                && authorizationCode1.getContextVersion() == 1);
    }

    @Test
    public void shouldNotFindCode() {
        String code = "unknownCode";
        TestObserver<AuthorizationCode> test = authorizationCodeRepository.findByCode(code).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertComplete();
        test.assertNoValues();
    }

    @Test
    public void shouldRemoveCode() throws InterruptedException {
        String code = "testCode";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId(code);
        authorizationCode.setCode(code);

        TestObserver<Void> testObserver = authorizationCodeRepository
                .create(authorizationCode)
                .ignoreElement()
                .andThen(authorizationCodeRepository.delete(code))
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        var deletionValidationObserver = authorizationCodeRepository.findByCode(code).test();
        deletionValidationObserver.await(10, TimeUnit.SECONDS);
        deletionValidationObserver.assertNoErrors();
        deletionValidationObserver.assertNoValues();
    }

    @Test
    public void shouldCreateWithLongClientId() {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId("test");
        authorizationCode.setCode("test");
        authorizationCode.setClientId("very-long-client-very-long-client-very-long-client-very-long-client-very-long-client-very-long-client");

        TestObserver<AuthorizationCode> observer = authorizationCodeRepository
                .create(authorizationCode).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }


    @Test
    public void shouldCreateWithResources() {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId("test");
        authorizationCode.setCode("test");
        authorizationCode.setClientId("client-id");
        authorizationCode.setResources(Set.of("one", "two"));

        TestObserver<AuthorizationCode> observer = authorizationCodeRepository
                .create(authorizationCode).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

}
