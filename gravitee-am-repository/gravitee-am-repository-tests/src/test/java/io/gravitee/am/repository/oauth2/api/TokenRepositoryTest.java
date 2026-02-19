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
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class TokenRepositoryTest extends AbstractOAuthTest {
    @Autowired
    private TokenRepository tokenRepository;

    @Test
    public void shouldNotFindTokensByJti() {
        TestObserver<AccessToken> accessObserver = tokenRepository.findAccessTokenByJti("unknown-access").test();
        accessObserver.awaitDone(10, TimeUnit.SECONDS);
        accessObserver.assertComplete();
        accessObserver.assertNoValues();
        accessObserver.assertNoErrors();

        TestObserver<RefreshToken> refreshObserver = tokenRepository.findRefreshTokenByJti("unknown-refresh").test();
        refreshObserver.awaitDone(10, TimeUnit.SECONDS);
        refreshObserver.assertComplete();
        refreshObserver.assertNoValues();
        refreshObserver.assertNoErrors();
    }

    @Test
    public void shouldCreateAndFindTokensByJti() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken("refresh-token");

        TestObserver<AccessToken> accessObserver = Completable.fromSingle(tokenRepository.create(accessToken))
                .andThen(tokenRepository.findAccessTokenByJti(accessToken.getToken()))
                .test();

        accessObserver.awaitDone(10, TimeUnit.SECONDS);
        accessObserver.assertComplete();
        accessObserver.assertValueCount(1);
        accessObserver.assertNoErrors();

        TestObserver<RefreshToken> refreshObserver = Completable.fromSingle(tokenRepository.create(refreshToken))
                .andThen(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()))
                .test();

        refreshObserver.awaitDone(10, TimeUnit.SECONDS);
        refreshObserver.assertComplete();
        refreshObserver.assertValueCount(1);
        refreshObserver.assertNoErrors();
    }

    @Test
    public void shouldFindAccessTokenByAuthorizationCode() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-auth");
        accessToken.setAuthorizationCode("auth-code");

        TestObserver<AccessToken> observer = Completable.fromSingle(tokenRepository.create(accessToken))
                .andThen(tokenRepository.findAccessTokenByAuthorizationCode(accessToken.getAuthorizationCode()))
                .test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldDeleteByJti() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-delete");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken("refresh-token-delete");

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(refreshToken).ignoreElement())
                .andThen(tokenRepository.deleteByJti(accessToken.getToken()))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());

        tokenRepository.deleteByJti(refreshToken.getToken()).blockingAwait();
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByUserId() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-user");
        accessToken.setSubject("user-id");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken("refresh-token-user");
        refreshToken.setSubject("user-id");

        AccessToken otherAccessToken = new AccessToken();
        otherAccessToken.setId(RandomString.generate());
        otherAccessToken.setToken("access-token-other-user");
        otherAccessToken.setSubject("other-user");

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(refreshToken).ignoreElement())
                .andThen(tokenRepository.create(otherAccessToken).ignoreElement())
                .andThen(tokenRepository.deleteByUserId("user-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherAccessToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdClientIdAndUserId() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-domain-client-user");
        accessToken.setDomain("domain-id");
        accessToken.setClient("client-id");
        accessToken.setSubject("user-id");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken("refresh-token-domain-client-user");
        refreshToken.setDomain("domain-id");
        refreshToken.setClient("client-id");
        refreshToken.setSubject("user-id");

        AccessToken otherAccessToken = new AccessToken();
        otherAccessToken.setId(RandomString.generate());
        otherAccessToken.setToken("access-token-other-domain");
        otherAccessToken.setDomain("domain-id2");
        otherAccessToken.setClient("client-id");
        otherAccessToken.setSubject("user-id");

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(refreshToken).ignoreElement())
                .andThen(tokenRepository.create(otherAccessToken).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdClientIdAndUserId("domain-id", "client-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherAccessToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndUserId() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-domain-user");
        accessToken.setDomain("domain-id");
        accessToken.setClient("client-id");
        accessToken.setSubject("user-id");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken("refresh-token-domain-user");
        refreshToken.setDomain("domain-id");
        refreshToken.setClient("client-id");
        refreshToken.setSubject("user-id");

        AccessToken otherAccessToken = new AccessToken();
        otherAccessToken.setId(RandomString.generate());
        otherAccessToken.setToken("access-token-other-domain-user");
        otherAccessToken.setDomain("domain-id2");
        otherAccessToken.setClient("client-id");
        otherAccessToken.setSubject("user-id");

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(refreshToken).ignoreElement())
                .andThen(tokenRepository.create(otherAccessToken).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndUserId("domain-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherAccessToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndClientId() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken("access-token-domain-client");
        accessToken.setDomain("domain-id");
        accessToken.setClient("client-id");
        accessToken.setSubject("user-id");

        AccessToken otherAccessToken = new AccessToken();
        otherAccessToken.setId(RandomString.generate());
        otherAccessToken.setToken("access-token-other-client");
        otherAccessToken.setDomain("domain-id");
        otherAccessToken.setClient("client-id2");
        otherAccessToken.setSubject("user-id");

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(otherAccessToken).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndClientId("domain-id", "client-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherAccessToken.getToken()).blockingGet());
    }
}
