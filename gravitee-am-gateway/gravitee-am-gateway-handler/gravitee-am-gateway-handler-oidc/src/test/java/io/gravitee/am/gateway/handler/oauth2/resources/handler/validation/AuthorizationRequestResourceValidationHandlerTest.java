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

import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthorizationRequestResourceValidationHandler
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestResourceValidationHandlerTest {

    @Mock
    private ResourceValidationService resourceValidationService;

    @Mock
    private RoutingContext routingContext;

    private AuthorizationRequestResourceValidationHandler handler;

    @Before
    public void setUp() {
        handler = new AuthorizationRequestResourceValidationHandler(resourceValidationService);
    }

    @Test
    public void shouldContinueWhenValidationSucceeds() {
        // Given
        AuthorizationRequest authRequest = new AuthorizationRequest();
        when(routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY)).thenReturn(authRequest);
        when(resourceValidationService.validate(any()))
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
        AuthorizationRequest authRequest = new AuthorizationRequest();
        when(routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY)).thenReturn(authRequest);
        InvalidResourceException exception = new InvalidResourceException("Invalid resource");
        when(resourceValidationService.validate(any()))
            .thenReturn(Completable.error(exception));

        // When
        handler.handle(routingContext);

        // Then
        verify(routingContext).fail(exception);
        verify(routingContext, never()).next();
    }

    @Test
    public void shouldFailWhenAuthorizationRequestMissingInContext() {
        // Given no authorizationRequest in context
        when(routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY)).thenReturn(null);

        // When
        handler.handle(routingContext);

        // Then
        verify(routingContext).fail(any(IllegalStateException.class));
        verify(routingContext, never()).next();
        verifyNoInteractions(resourceValidationService);
    }
}
