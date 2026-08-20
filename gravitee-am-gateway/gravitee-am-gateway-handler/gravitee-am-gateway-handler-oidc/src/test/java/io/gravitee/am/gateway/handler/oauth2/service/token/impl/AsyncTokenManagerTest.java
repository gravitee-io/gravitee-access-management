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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AsyncTokenManagerTest {

    private static final String DOMAIN_ID = "domain#1";
    private static final String CLIENT_ID = "client#1";

    @Mock
    private TokenRepository tokenRepository;

    private AsyncTokenManager tokenManager;

    private AccessToken accessToken;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        tokenManager = new AsyncTokenManager(tokenRepository);

        accessToken = new AccessToken();
        accessToken.setToken("at-jti");
        accessToken.setDomain(DOMAIN_ID);
        accessToken.setClient(CLIENT_ID);

        refreshToken = new RefreshToken();
        refreshToken.setToken("rt-jti");
        refreshToken.setDomain(DOMAIN_ID);
        refreshToken.setClient(CLIENT_ID);
    }

    @Test
    void shouldNotWaitForStorage() {
        when(tokenRepository.create(accessToken, refreshToken)).thenReturn(Completable.never());

        tokenManager.storeTokens(accessToken, refreshToken)
                .test()
                .assertComplete();
    }

    @Test
    void shouldStoreTokensInBackground() throws Exception {
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(accessToken, refreshToken)).thenReturn(Completable.fromAction(stored::countDown));

        tokenManager.storeTokens(accessToken, refreshToken)
                .test()
                .assertComplete();

        assertTrue(stored.await(10, TimeUnit.SECONDS));
    }

    @Test
    void shouldStoreAccessTokenWithoutRefreshToken() throws Exception {
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(accessToken, null)).thenReturn(Completable.fromAction(stored::countDown));

        tokenManager.storeTokens(accessToken, null)
                .test()
                .assertComplete();

        assertTrue(stored.await(10, TimeUnit.SECONDS));
    }

    @Test
    void shouldNotPropagateStorageError() {
        when(tokenRepository.create(accessToken, refreshToken)).thenReturn(Completable.error(new TechnicalException()));

        tokenManager.storeTokens(accessToken, refreshToken)
                .test()
                .assertComplete();
    }

    @Test
    void shouldWaitForPendingWritesOnStop() throws Exception {
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(accessToken, refreshToken)).thenReturn(Completable.complete()
                .delay(200, TimeUnit.MILLISECONDS)
                .doOnComplete(stored::countDown));

        tokenManager.storeTokens(accessToken, refreshToken)
                .test()
                .assertComplete();
        tokenManager.doStop();

        assertEquals(0, stored.getCount());
    }

}
