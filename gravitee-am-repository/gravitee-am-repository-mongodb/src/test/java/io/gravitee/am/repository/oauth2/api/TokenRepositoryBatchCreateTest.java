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
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The batch insert relies on an ordered insertMany, which is not a transaction: MongoDB stops at the
 * failing document but keeps the ones already written. Unlike the JDBC batch, it gives a single
 * round-trip, not all-or-nothing. Standalone deployments cannot provide the latter.
 *
 * @author GraviteeSource Team
 */
public class TokenRepositoryBatchCreateTest extends AbstractOAuthTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private TokenRepository tokenRepository;

    @Test
    public void shouldKeepAccessTokenWhenRefreshTokenDocumentFails() {
        String suffix = shortSuffix();
        String domain = "domain-" + suffix;

        AccessToken existingToken = newAccessToken("existing-at-" + suffix, domain);
        tokenRepository.create(existingToken).ignoreElement().blockingAwait();

        AccessToken accessToken = newAccessToken("batch-at-" + suffix, domain);
        RefreshToken refreshToken = newRefreshToken("batch-rt-" + suffix, domain);
        refreshToken.setId(existingToken.getId());

        TestObserver<Void> observer = tokenRepository.create(accessToken, refreshToken).test();
        observer.awaitDone(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        assertNotNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
    }

    @Test
    public void shouldNotStoreRefreshTokenWhenAccessTokenDocumentFails() {
        String suffix = shortSuffix();
        String domain = "domain-" + suffix;

        AccessToken existingToken = newAccessToken("existing-at-" + suffix, domain);
        tokenRepository.create(existingToken).ignoreElement().blockingAwait();

        AccessToken accessToken = newAccessToken("batch-at-" + suffix, domain);
        accessToken.setId(existingToken.getId());
        RefreshToken refreshToken = newRefreshToken("batch-rt-" + suffix, domain);

        TestObserver<Void> observer = tokenRepository.create(accessToken, refreshToken).test();
        observer.awaitDone(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        assertNull(tokenRepository.findAccessTokenByJti(accessToken.getToken()).blockingGet());
        assertNull(tokenRepository.findRefreshTokenByJti(refreshToken.getToken()).blockingGet());
    }

    private AccessToken newAccessToken(String token, String domain) {
        AccessToken accessToken = new AccessToken();
        accessToken.setId(RandomString.generate());
        accessToken.setToken(token);
        accessToken.setDomain(domain);
        accessToken.setAllParentJtis(new HashSet<>());
        return accessToken;
    }

    private RefreshToken newRefreshToken(String token, String domain) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(RandomString.generate());
        refreshToken.setToken(token);
        refreshToken.setDomain(domain);
        refreshToken.setAllParentJtis(new HashSet<>());
        return refreshToken;
    }

    private String shortSuffix() {
        return RandomString.generate().substring(0, 8);
    }
}
