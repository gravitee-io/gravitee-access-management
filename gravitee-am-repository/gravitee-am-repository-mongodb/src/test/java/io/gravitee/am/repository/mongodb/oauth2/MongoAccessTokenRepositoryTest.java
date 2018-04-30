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

import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.AccessTokenCriteria;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.UUID;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAccessTokenRepositoryTest extends AbstractOAuth2RepositoryTest {

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Test
    public void shouldNotFindToken() {
        TestObserver<AccessToken> observer = accessTokenRepository.findByToken("unknown-token").test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindToken() {
        AccessToken token = new AccessToken();
        token.setId(UUID.randomUUID().toString());
        token.setToken("my-token");

        TestObserver<AccessToken> observer = accessTokenRepository
                .create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByToken("my-token"))
                .test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindByClientIdAndSubject() {
        AccessToken token = new AccessToken();
        token.setId(UUID.randomUUID().toString());
        token.setToken("my-token");
        token.setClientId("my-client-id");
        token.setSubject("my-subject");

        TestObserver<AccessToken> observer = accessTokenRepository.create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByClientIdAndSubject("my-client-id", "my-subject"))
                .test();


        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(accessToken -> accessToken.getSubject().equals("my-subject") && accessToken.getClientId().equals("my-client-id"));
    }

    @Test
    public void shouldFindByClientId() {
        AccessToken token = new AccessToken();
        token.setId(UUID.randomUUID().toString());
        token.setToken("my-token");
        token.setClientId("my-client-id-2");

        TestObserver<AccessToken> observer = accessTokenRepository.create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByClientId("my-client-id-2"))
                .test();

        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);
    }

    @Test
    public void shouldFindByCriteria() {
        AccessToken token = new AccessToken();
        token.setId(UUID.randomUUID().toString());
        token.setToken("my-token");
        token.setClientId("my-client-id-3");
        token.setSubject("my-subject-3");
        token.setScopes(Collections.singleton("read"));

        AccessTokenCriteria.Builder builder = new AccessTokenCriteria.Builder();
        builder.clientId("my-client-id-3");
        builder.subject("my-subject-3");
        builder.scopes(Collections.singleton("read"));

        TestObserver<AccessToken> observer = accessTokenRepository.create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByCriteria(builder.build()))
                .test();

        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(accessToken -> accessToken.getToken().equals("my-token") && accessToken.getScopes().contains("read"));
    }

}
