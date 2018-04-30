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
package io.gravitee.am.repository.mongodb.oauth2;

import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAuthorizationCodeRepositoryTest extends AbstractOAuth2RepositoryTest {

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Test
    public void shouldStoreCode() {
        String code = "testCode";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(code);

        authorizationCodeRepository.create(authorizationCode).blockingGet();

        TestObserver<AuthorizationCode> testObserver = authorizationCodeRepository.findByCode(code).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(authorizationCode1 -> authorizationCode1.getCode().equals(code));
    }

    @Test
    public void shouldNotFindCode() {
        String code = "unknownCode";
        authorizationCodeRepository.findByCode(code).test().assertEmpty();
    }

    @Test
    public void shouldRemoveCode() {
        String code = "testCode";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId(code);
        authorizationCode.setCode(code);

        authorizationCodeRepository
                .create(authorizationCode)
                .toCompletable()
                .andThen(authorizationCodeRepository.delete(code))
                .test().assertEmpty();
    }

}