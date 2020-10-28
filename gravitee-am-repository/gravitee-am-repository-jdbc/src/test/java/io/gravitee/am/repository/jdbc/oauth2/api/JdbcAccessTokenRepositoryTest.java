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
package io.gravitee.am.repository.jdbc.oauth2.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.jdbc.oauth2.AbstractOauthJdbcTest;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcAccessTokenRepositoryTest extends AbstractOauthJdbcTest {
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
        token.setId(RandomString.generate());
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
    public void shouldDeleteByToken() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token-todelete");

        TestObserver<AccessToken> observer = accessTokenRepository
                .create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByToken(token.getToken()))
                .test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();

        TestObserver<Void> testDelete = accessTokenRepository.delete(token.getToken()).test();
        testDelete.awaitTerminalEvent();
        testDelete.assertNoErrors();
    }

    @Test
    public void shouldFindAuthorizationCode() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");
        token.setAuthorizationCode("some-auth-code");

        TestObserver<AccessToken> observer = accessTokenRepository
                .create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByAuthorizationCode(token.getAuthorizationCode()))
                .test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }
    @Test
    public void shouldNotFindByUnknownAuthorizationCode() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");
        token.setAuthorizationCode("some-auth-code");

        TestObserver<AccessToken> observer = accessTokenRepository
                .create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByAuthorizationCode("unknown"))
                .test();

        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindByClientIdAndSubject() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");
        token.setClient("my-client-id");
        token.setSubject("my-subject");

        TestObserver<AccessToken> observer = accessTokenRepository.create(token)
                .toCompletable()
                .andThen(accessTokenRepository.findByClientIdAndSubject("my-client-id", "my-subject"))
                .test();


        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(accessToken -> accessToken.getSubject().equals("my-subject") && accessToken.getClient().equals("my-client-id"));
    }

    @Test
    public void shouldFindByClientId() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");
        token.setClient("my-client-id-2");

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
    public void shouldCountByClientId() {
        AccessToken token = new AccessToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");
        token.setClient("my-client-id-count");

        TestObserver<Long> observer = accessTokenRepository.create(token)
                .toCompletable()
                .andThen(accessTokenRepository.countByClientId("my-client-id-count"))
                .test();

        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(new Long(1));
    }

    @Test
    public void shouldDeleteByDomainIdClientIdAndUserId() {
        AccessToken token1 = new AccessToken();
        token1.setId("my-token");
        token1.setToken("my-token");
        token1.setClient("client-id");
        token1.setDomain("domain-id");
        token1.setSubject("user-id");

        AccessToken token2 = new AccessToken();
        token2.setId("my-token2");
        token2.setToken("my-token2");
        token2.setClient("client-id2");
        token2.setDomain("domain-id2");
        token2.setSubject("user-id2");

        TestObserver<AccessToken> testObserver = accessTokenRepository
                .bulkWrite(Arrays.asList(token1, token2))
                .andThen(accessTokenRepository.deleteByDomainIdClientIdAndUserId("domain-id", "client-id", "user-id"))
                .andThen(accessTokenRepository.findByToken("my-token"))
                .test();
        testObserver.awaitTerminalEvent();
        assertEquals(0, testObserver.valueCount());

        assertNotNull(accessTokenRepository.findByToken("my-token2").blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndUserId() {
        AccessToken token1 = new AccessToken();
        token1.setId("my-token");
        token1.setToken("my-token");
        token1.setClient("client-id");
        token1.setDomain("domain-id");
        token1.setSubject("user-id");

        AccessToken token2 = new AccessToken();
        token2.setId("my-token2");
        token2.setToken("my-token2");
        token2.setClient("client-id2");
        token2.setDomain("domain-id2");
        token2.setSubject("user-id2");

        TestObserver<AccessToken> testObservable = accessTokenRepository
                .bulkWrite(Arrays.asList(token1, token2))
                .andThen(accessTokenRepository.deleteByDomainIdAndUserId("domain-id", "user-id"))
                .andThen(accessTokenRepository.findByToken("my-token"))
                .test();
        testObservable.awaitTerminalEvent();
        assertEquals(0, testObservable.valueCount());

        assertNotNull(accessTokenRepository.findByToken("my-token2").blockingGet());
    }

}
