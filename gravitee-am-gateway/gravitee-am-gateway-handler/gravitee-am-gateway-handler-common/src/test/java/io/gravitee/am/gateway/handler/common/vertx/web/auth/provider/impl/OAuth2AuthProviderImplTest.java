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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.AsyncResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OAuth2AuthProviderImplTest {

    private static final String TOKEN = "test-token";
    private static final String DOMAIN = "test-domain";
    private static final String CLIENT_ID = "test-client-id";

    @Mock
    @org.springframework.beans.factory.annotation.Qualifier("AccessTokenIntrospection")
    private IntrospectionTokenService introspectionTokenService;

    @Mock
    private ClientSyncService clientSyncService;

    @InjectMocks
    private OAuth2AuthProviderImpl oAuth2AuthProvider;

    @Before
    public void setUp() {
        // Reset mocks before each test
        reset(introspectionTokenService, clientSyncService);
    }

    @Test
    public void shouldDecodeTokenSuccessfullyWhenClientIsFound() throws InterruptedException {
        // Given
        JWT jwt = new JWT(Map.of(
                Claims.SUB, "test-sub",
                Claims.AUD, CLIENT_ID,
                Claims.DOMAIN, DOMAIN
        ));
        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setDomain(DOMAIN);

        when(introspectionTokenService.introspect(TOKEN, false))
                .thenReturn(Maybe.just(jwt));
        when(clientSyncService.findByDomainAndClientId(DOMAIN, CLIENT_ID))
                .thenReturn(Maybe.just(client));

        CountDownLatch latch = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        AsyncResult<OAuth2AuthResponse>[] result = new AsyncResult[1];

        // When
        oAuth2AuthProvider.decodeToken(TOKEN, false, handler -> {
            result[0] = handler;
            latch.countDown();
        });

        // Then
        assertTrue("Handler should be called", latch.await(1, TimeUnit.SECONDS));
        assertNotNull("Result should not be null", result[0]);
        assertTrue("Result should be successful", result[0].succeeded());
        assertNotNull("Response should not be null", result[0].result());
        assertEquals("JWT should match", jwt, result[0].result().getToken());
        assertEquals("Client should match", client, result[0].result().getClient());

        verify(introspectionTokenService).introspect(TOKEN, false);
        verify(clientSyncService).findByDomainAndClientId(DOMAIN, CLIENT_ID);
    }

    @Test
    public void shouldFailWhenClientIsNotFound() throws InterruptedException {
        // Given
        JWT jwt = new JWT(Map.of(
                Claims.SUB, "test-sub",
                Claims.AUD, CLIENT_ID,
                Claims.DOMAIN, DOMAIN
        ));

        when(introspectionTokenService.introspect(TOKEN, false))
                .thenReturn(Maybe.just(jwt));
        when(clientSyncService.findByDomainAndClientId(DOMAIN, CLIENT_ID))
                .thenReturn(Maybe.empty());

        CountDownLatch latch = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        AsyncResult<OAuth2AuthResponse>[] result = new AsyncResult[1];

        // When
        oAuth2AuthProvider.decodeToken(TOKEN, false, handler -> {
            result[0] = handler;
            latch.countDown();
        });

        // Then
        assertTrue("Handler should be called", latch.await(1, TimeUnit.SECONDS));
        assertNotNull("Result should not be null", result[0]);
        assertTrue("Result should be failed", result[0].failed());
        assertNotNull("Failure cause should not be null", result[0].cause());
        assertTrue("Failure should be InvalidTokenException", 
                result[0].cause() instanceof InvalidTokenException);
        
        InvalidTokenException exception = (InvalidTokenException) result[0].cause();
        assertEquals("Error message should match", "The token is invalid", exception.getMessage());
        assertEquals("Error details should match", "Client not found", exception.getDetails());

        verify(introspectionTokenService).introspect(TOKEN, false);
        verify(clientSyncService).findByDomainAndClientId(DOMAIN, CLIENT_ID);
    }

    @Test
    public void shouldFailWhenTokenIntrospectionFails() throws InterruptedException {
        // Given
        RuntimeException introspectionError = new RuntimeException("Token introspection failed");

        when(introspectionTokenService.introspect(TOKEN, true))
                .thenReturn(Maybe.error(introspectionError));

        CountDownLatch latch = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        AsyncResult<OAuth2AuthResponse>[] result = new AsyncResult[1];

        // When
        oAuth2AuthProvider.decodeToken(TOKEN, true, handler -> {
            result[0] = handler;
            latch.countDown();
        });

        // Then
        assertTrue("Handler should be called", latch.await(1, TimeUnit.SECONDS));
        assertNotNull("Result should not be null", result[0]);
        assertTrue("Result should be failed", result[0].failed());
        assertEquals("Failure cause should match", introspectionError, result[0].cause());

        verify(introspectionTokenService).introspect(TOKEN, true);
        verify(clientSyncService, never()).findByDomainAndClientId(anyString(), anyString());
    }
}

