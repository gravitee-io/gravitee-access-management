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
        AccessToken accessToken = newAccessToken("access-token", null, null, null, null);
        RefreshToken refreshToken = newRefreshToken("refresh-token", null, null, null, null);

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
        AccessToken accessToken = newAccessToken("access-token-auth", null, null, null, null);
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
        AccessToken accessToken = newAccessToken("access-token-delete", null, null, null, null);
        RefreshToken refreshToken = newRefreshToken("refresh-token-delete", null, null, null, null);

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
    public void shouldDeleteRecursivelyByParentSubjectJti() {
        AccessToken rootToken = newAccessToken("root-token", null, null, null, null);
        AccessToken childTokenA = newAccessToken("child-token-a", null, null, null, rootToken.getToken());
        AccessToken childTokenB = newAccessToken("child-token-b", null, null, null, rootToken.getToken());
        AccessToken childTokenC = newAccessToken("child-token-c", null, null, null, rootToken.getToken());
        RefreshToken grandChildTokenA1 = newRefreshToken("grand-child-token-a1", null, null, null, childTokenA.getToken());
        AccessToken grandChildTokenA2 = newAccessToken("grand-child-token-a2", null, null, null, childTokenA.getToken());
        RefreshToken grandChildTokenB1 = newRefreshToken("grand-child-token-b1", null, null, null, childTokenB.getToken());
        AccessToken grandChildTokenC1 = newAccessToken("grand-child-token-c1", null, null, null, childTokenC.getToken());
        AccessToken otherToken = newAccessToken("other-token", null, null, null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childTokenA).ignoreElement())
                .andThen(tokenRepository.create(childTokenB).ignoreElement())
                .andThen(tokenRepository.create(childTokenC).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenA1).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenA2).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenB1).ignoreElement())
                .andThen(tokenRepository.create(grandChildTokenC1).ignoreElement())
                .andThen(tokenRepository.create(otherToken).ignoreElement())
                .andThen(tokenRepository.deleteByJti(rootToken.getToken()))
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
    public void shouldDeleteRecursivelyByParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("root-actor-" + suffix, null, null, null, null, null);
        AccessToken childToken = newAccessToken("child-actor-" + suffix, null, null, null, null, rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("grand-child-actor-" + suffix, null, null, null, childToken.getToken(), null);
        AccessToken outsiderToken = newAccessToken("outsider-actor-" + suffix, null, null, null, null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(outsiderToken).ignoreElement())
                .andThen(tokenRepository.deleteByJti(rootToken.getToken()))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(outsiderToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByUserId() {
        AccessToken accessToken = newAccessToken("access-token-user", null, null, "user-id", null);
        RefreshToken refreshToken = newRefreshToken("refresh-token-user", null, null, "user-id", null);
        AccessToken otherAccessToken = newAccessToken("access-token-other-user", null, null, "other-user", null);

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
    public void shouldDeleteByUserIdRecursivelyByParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtuar-" + suffix, null, null, "user-id", null, null);
        AccessToken childToken = newAccessToken("ctuar-" + suffix, null, null, "other-user", null, rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctuar-" + suffix, null, null, "another-user", childToken.getToken(), null);
        AccessToken outsiderToken = newAccessToken("otuar-" + suffix, null, null, "other-user", null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(outsiderToken).ignoreElement())
                .andThen(tokenRepository.deleteByUserId("user-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(outsiderToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdClientIdAndUserId() {
        AccessToken accessToken = newAccessToken("access-token-domain-client-user", "domain-id", "client-id", "user-id", null);
        RefreshToken refreshToken = newRefreshToken("refresh-token-domain-client-user", "domain-id", "client-id", "user-id", null);
        AccessToken otherAccessToken = newAccessToken("access-token-other-domain", "domain-id2", "client-id", "user-id", null);

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
        AccessToken accessToken = newAccessToken("access-token-domain-user", "domain-id", "client-id", "user-id", null);
        RefreshToken refreshToken = newRefreshToken("refresh-token-domain-user", "domain-id", "client-id", "user-id", null);
        AccessToken otherAccessToken = newAccessToken("access-token-other-domain-user", "domain-id2", "client-id", "user-id", null);

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
        AccessToken accessToken = newAccessToken("access-token-domain-client", "domain-id", "client-id", "user-id", null);
        AccessToken otherAccessToken = newAccessToken("access-token-other-client", "domain-id", "client-id2", "user-id", null);

        tokenRepository.create(accessToken).ignoreElement()
                .andThen(tokenRepository.create(otherAccessToken).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndClientId("domain-id", "client-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherAccessToken.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByUserIdRecursively() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtur-" + suffix, null, null, "user-id", null);
        AccessToken childToken = newAccessToken("ctur-" + suffix, null, null, "other-user", rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctur-" + suffix, null, null, "another-user", childToken.getToken());
        AccessToken otherTreeRoot = newAccessToken("otur-" + suffix, null, null, "other-user", null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(otherTreeRoot).ignoreElement())
                .andThen(tokenRepository.deleteByUserId("user-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(otherTreeRoot.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdClientIdAndUserIdRecursively() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtdcur-" + suffix, "domain-id", "client-id", "user-id", null);
        AccessToken childToken = newAccessToken("ctdcur-" + suffix, "other-domain", "other-client", "other-user", rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctdcur-" + suffix, "third-domain", "third-client", "third-user", childToken.getToken());
        AccessToken sameDomainClientDifferentUser = newAccessToken("sdcdu-" + suffix, "domain-id", "client-id", "other-user", null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainClientDifferentUser).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdClientIdAndUserId("domain-id", "client-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainClientDifferentUser.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdClientIdAndUserIdRecursivelyByParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtdcuar-" + suffix, "domain-id", "client-id", "user-id", null, null);
        AccessToken childToken = newAccessToken("ctdcuar-" + suffix, "other-domain", "other-client", "other-user", null, rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctdcuar-" + suffix, "third-domain", "third-client", "third-user", childToken.getToken(), null);
        AccessToken sameDomainClientDifferentUser = newAccessToken("sdcduar-" + suffix, "domain-id", "client-id", "other-user", null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainClientDifferentUser).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdClientIdAndUserId("domain-id", "client-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainClientDifferentUser.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndUserIdRecursively() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtdur-" + suffix, "domain-id", "client-id", "user-id", null);
        AccessToken childToken = newAccessToken("ctdur-" + suffix, "other-domain", "other-client", "other-user", rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctdur-" + suffix, "another-domain", "another-client", "another-user", childToken.getToken());
        AccessToken sameDomainDifferentUser = newAccessToken("sddu-" + suffix, "domain-id", "client-id", "other-user", null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainDifferentUser).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndUserId("domain-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainDifferentUser.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndUserIdRecursivelyByParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtduar-" + suffix, "domain-id", "client-id", "user-id", null, null);
        AccessToken childToken = newAccessToken("ctduar-" + suffix, "other-domain", "other-client", "other-user", null, rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctduar-" + suffix, "another-domain", "another-client", "another-user", childToken.getToken(), null);
        AccessToken sameDomainDifferentUser = newAccessToken("sdduar-" + suffix, "domain-id", "client-id", "other-user", null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainDifferentUser).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndUserId("domain-id", UserId.internal("user-id")))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainDifferentUser.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndClientIdRecursively() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtdcr-" + suffix, "domain-id", "client-id", "user-id", null);
        AccessToken childToken = newAccessToken("ctdcr-" + suffix, "other-domain", "other-client", "other-user", rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctdcr-" + suffix, "another-domain", "another-client", "another-user", childToken.getToken());
        AccessToken sameDomainDifferentClient = newAccessToken("sddc-" + suffix, "domain-id", "client-id-2", "user-id", null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainDifferentClient).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndClientId("domain-id", "client-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainDifferentClient.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteByDomainIdAndClientIdRecursivelyByParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtdcar-" + suffix, "domain-id", "client-id", "user-id", null, null);
        AccessToken childToken = newAccessToken("ctdcar-" + suffix, "other-domain", "other-client", "other-user", null, rootToken.getToken());
        RefreshToken grandChildToken = newRefreshToken("gctdcar-" + suffix, "another-domain", "another-client", "another-user", childToken.getToken(), null);
        AccessToken sameDomainDifferentClient = newAccessToken("sddcar-" + suffix, "domain-id", "client-id-2", "user-id", null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childToken).ignoreElement())
                .andThen(tokenRepository.create(grandChildToken).ignoreElement())
                .andThen(tokenRepository.create(sameDomainDifferentClient).ignoreElement())
                .andThen(tokenRepository.deleteByDomainIdAndClientId("domain-id", "client-id"))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildToken.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(sameDomainDifferentClient.getToken()).blockingGet());
    }

    @Test
    public void shouldDeleteRecursivelyAcrossParentSubjectAndParentActorJti() {
        final String suffix = shortSuffix();

        AccessToken rootToken = newAccessToken("rtsa-" + suffix, null, null, null, null, null);
        AccessToken childBySubject = newAccessToken("cts-" + suffix, null, null, null, rootToken.getToken(), null);
        AccessToken childByActor = newAccessToken("cta-" + suffix, null, null, null, null, childBySubject.getToken());
        RefreshToken grandChildBySubject = newRefreshToken("gcts-" + suffix, null, null, null, childByActor.getToken(), null);
        AccessToken outsiderToken = newAccessToken("otsa-" + suffix, null, null, null, null, null);

        tokenRepository.create(rootToken).ignoreElement()
                .andThen(tokenRepository.create(childBySubject).ignoreElement())
                .andThen(tokenRepository.create(childByActor).ignoreElement())
                .andThen(tokenRepository.create(grandChildBySubject).ignoreElement())
                .andThen(tokenRepository.create(outsiderToken).ignoreElement())
                .andThen(tokenRepository.deleteByJti(rootToken.getToken()))
                .blockingAwait();

        assertNull(tokenRepository.findAccessTokenByJti(rootToken.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childBySubject.getToken()).blockingGet());
        assertNull(tokenRepository.findAccessTokenByJti(childByActor.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(grandChildBySubject.getToken()).blockingGet());
        assertNotNull(tokenRepository.findAccessTokenByJti(outsiderToken.getToken()).blockingGet());
    }

    private AccessToken newAccessToken(String token, String domain, String client, String subject, String parentSubjectJti) {
        return newAccessToken(token, domain, client, subject, parentSubjectJti, null);
    }

    private AccessToken newAccessToken(String token, String domain, String client, String subject, String parentSubjectJti, String parentActorJti) {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken(token);
        accessToken.setDomain(domain);
        accessToken.setClient(client);
        accessToken.setSubject(subject);
        accessToken.setParentSubjectJti(parentSubjectJti);
        accessToken.setParentActorJti(parentActorJti);
        return accessToken;
    }

    private RefreshToken newRefreshToken(String token, String domain, String client, String subject, String parentSubjectJti) {
        return newRefreshToken(token, domain, client, subject, parentSubjectJti, null);
    }

    private RefreshToken newRefreshToken(String token, String domain, String client, String subject, String parentSubjectJti, String parentActorJti) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken(token);
        refreshToken.setDomain(domain);
        refreshToken.setClient(client);
        refreshToken.setSubject(subject);
        refreshToken.setParentSubjectJti(parentSubjectJti);
        refreshToken.setParentActorJti(parentActorJti);
        return refreshToken;
    }

    private String shortSuffix() {
        return RandomString.generate().substring(0, 8);
    }
}
