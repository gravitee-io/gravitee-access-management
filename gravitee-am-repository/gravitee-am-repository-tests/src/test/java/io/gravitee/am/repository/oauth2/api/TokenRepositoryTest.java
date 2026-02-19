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
    public void shouldDeleteRecursivelyByParentJti() {
        AccessToken rootToken = new AccessToken();
        rootToken.setId(RandomString.generate());
        rootToken.setToken("root-token");

        AccessToken childTokenA = new AccessToken();
        childTokenA.setId(RandomString.generate());
        childTokenA.setToken("child-token-a");
        childTokenA.setParentJti(rootToken.getToken());

        AccessToken childTokenB = new AccessToken();
        childTokenB.setId(RandomString.generate());
        childTokenB.setToken("child-token-b");
        childTokenB.setParentJti(rootToken.getToken());

        AccessToken childTokenC = new AccessToken();
        childTokenC.setId(RandomString.generate());
        childTokenC.setToken("child-token-c");
        childTokenC.setParentJti(rootToken.getToken());

        RefreshToken grandChildTokenA1 = new RefreshToken();
        grandChildTokenA1.setId(RandomString.generate());
        grandChildTokenA1.setToken("grand-child-token-a1");
        grandChildTokenA1.setParentJti(childTokenA.getToken());

        AccessToken grandChildTokenA2 = new AccessToken();
        grandChildTokenA2.setId(RandomString.generate());
        grandChildTokenA2.setToken("grand-child-token-a2");
        grandChildTokenA2.setParentJti(childTokenA.getToken());

        RefreshToken grandChildTokenB1 = new RefreshToken();
        grandChildTokenB1.setId(RandomString.generate());
        grandChildTokenB1.setToken("grand-child-token-b1");
        grandChildTokenB1.setParentJti(childTokenB.getToken());

        AccessToken grandChildTokenC1 = new AccessToken();
        grandChildTokenC1.setId(RandomString.generate());
        grandChildTokenC1.setToken("grand-child-token-c1");
        grandChildTokenC1.setParentJti(childTokenC.getToken());

        AccessToken otherToken = new AccessToken();
        otherToken.setId(RandomString.generate());
        otherToken.setToken("other-token");

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childTokenA).ignoreElement())
                .andThen(tokenRepository.create(childTokenB).ignoreElement())
                .andThen(tokenRepository.create(childTokenC).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenA1).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenA2).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenB1).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenC1).ignoreElement())
                .andThen(tokenRepository.create(otherToken).ignoreElement())
                .andThen(tokenRepository.deleteRecursivelyByParentJti(rootToken.getToken()))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childTokenA.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childTokenB.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childTokenC.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildTokenA1.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(grandChildTokenA2.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildTokenB1.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(grandChildTokenC1.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherToken.getToken()).blockingGet());
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
