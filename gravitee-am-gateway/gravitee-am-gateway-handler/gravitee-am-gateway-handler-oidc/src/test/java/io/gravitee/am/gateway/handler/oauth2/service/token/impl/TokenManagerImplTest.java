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

import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class TokenManagerImplTest {

    private static final String DOMAIN_ID = "domain#1";
    private static final String CLIENT_ID = "client#1";

    @Mock
    private BackwardCompatibleTokenRepository tokenRepository;

    @InjectMocks
    private TokenManagerImpl tokenManager;

    private AccessToken accessToken;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
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
    void shouldWaitForStorageWhenAsyncStoreIsDisabled() {
        tokenManager.setAsyncStore(false);
        when(tokenRepository.create(accessToken)).thenReturn(Single.never());

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertNotComplete();
    }

    @Test
    void shouldNotWaitForStorageWhenAsyncStoreIsEnabled() {
        tokenManager.setAsyncStore(true);
        when(tokenRepository.create(accessToken)).thenReturn(Single.never());

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertComplete();
    }

    @Test
    void shouldStoreAccessTokenInBackgroundWhenAsyncStoreIsEnabled() throws Exception {
        tokenManager.setAsyncStore(true);
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(accessToken)).thenReturn(Single.fromCallable(() -> {
            stored.countDown();
            return accessToken;
        }));

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertComplete();

        assertTrue(stored.await(10, TimeUnit.SECONDS));
    }

    @Test
    void shouldStoreRefreshTokenInBackgroundWhenAsyncStoreIsEnabled() throws Exception {
        tokenManager.setAsyncStore(true);
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(refreshToken)).thenReturn(Single.fromCallable(() -> {
            stored.countDown();
            return refreshToken;
        }));

        tokenManager.storeRefreshToken(refreshToken, client(null))
                .test()
                .assertComplete();

        assertTrue(stored.await(10, TimeUnit.SECONDS));
    }

    @Test
    void shouldPropagateStorageErrorWhenAsyncStoreIsDisabled() {
        tokenManager.setAsyncStore(false);
        when(tokenRepository.create(accessToken)).thenReturn(Single.error(new TechnicalException()));

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertError(TechnicalException.class);
    }

    @Test
    void shouldNotPropagateStorageErrorWhenAsyncStoreIsEnabled() {
        tokenManager.setAsyncStore(true);
        when(tokenRepository.create(accessToken)).thenReturn(Single.error(new TechnicalException()));

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertComplete();
    }

    @Test
    void shouldStoreAsynchronouslyWhenClientOptsInOnDisabledGateway() {
        tokenManager.setAsyncStore(false);
        when(tokenRepository.create(accessToken)).thenReturn(Single.never());

        tokenManager.storeAccessToken(accessToken, client(Boolean.TRUE))
                .test()
                .assertComplete();
    }

    @Test
    void shouldStoreAsynchronouslyWhenClientDoesNotOptInOnEnabledGateway() {
        tokenManager.setAsyncStore(true);
        when(tokenRepository.create(accessToken)).thenReturn(Single.never());

        tokenManager.storeAccessToken(accessToken, client(Boolean.FALSE))
                .test()
                .assertComplete();
    }

    @Test
    void shouldStoreSynchronouslyWhenClientDoesNotOptInOnDisabledGateway() {
        tokenManager.setAsyncStore(false);
        when(tokenRepository.create(accessToken)).thenReturn(Single.never());

        tokenManager.storeAccessToken(accessToken, client(Boolean.FALSE))
                .test()
                .assertNotComplete();
    }

    @Test
    void shouldWaitForPendingWritesOnStop() throws Exception {
        tokenManager.setAsyncStore(true);
        final CountDownLatch stored = new CountDownLatch(1);
        when(tokenRepository.create(accessToken)).thenReturn(Single.just(accessToken)
                .delay(200, TimeUnit.MILLISECONDS)
                .doOnSuccess(token -> stored.countDown()));

        tokenManager.storeAccessToken(accessToken, client(null))
                .test()
                .assertComplete();
        tokenManager.doStop();

        assertEquals(0, stored.getCount());
    }

    private Client client(Boolean asyncTokenStore) {
        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setAsyncTokenStore(asyncTokenStore);
        return client;
    }
}
