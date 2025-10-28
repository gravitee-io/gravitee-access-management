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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.validation;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.net.ssl.SSLSession;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenRequestResourceValidationHandler
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenRequestResourceValidationHandlerTest {

    @Mock
    private ResourceValidationService resourceValidationService;

    @Mock
    private Domain domain;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpRequest;

    @Mock
    private Client client;

    @Mock
    private io.vertx.core.http.HttpServerRequest coreRequest;

    @Mock
    private HttpServerResponse coreResponse;

    @Mock
    private HttpConnection connection;

    @Mock
    private SSLSession sslSession;

    private TokenRequestResourceValidationHandler handler;

    @Before
    public void setUp() {
        handler = new TokenRequestResourceValidationHandler(resourceValidationService, domain);
        
        // Setup basic mocks
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(routingContext.request()).thenReturn(httpRequest);
        
        // Setup RxJava MultiMap
        io.vertx.rxjava3.core.MultiMap rxMultiMap = mock(io.vertx.rxjava3.core.MultiMap.class);
        when(httpRequest.params()).thenReturn(rxMultiMap);
        
        // Setup core delegate mocks - only what TokenRequestFactory actually needs
        when(httpRequest.getDelegate()).thenReturn(coreRequest);
        when(coreRequest.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(coreRequest.response()).thenReturn(coreResponse);
        when(coreRequest.method()).thenReturn(HttpMethod.POST);
    }

    @Test
    public void shouldContinueWhenValidationSucceeds() {
        // Given
        when(resourceValidationService.validate(any(), any(), any()))
            .thenReturn(Completable.complete());

        // When
        handler.handle(routingContext);

        // Then
        verify(routingContext).next();
        verify(routingContext, never()).fail(any());
    }

    @Test
    public void shouldFailWhenValidationFails() {
        // Given
        InvalidResourceException exception = new InvalidResourceException("Invalid resource");
        when(resourceValidationService.validate(any(), any(), any()))
            .thenReturn(Completable.error(exception));

        // When
        handler.handle(routingContext);

        // Then
        verify(routingContext).fail(exception);
        verify(routingContext, never()).next();
    }
}