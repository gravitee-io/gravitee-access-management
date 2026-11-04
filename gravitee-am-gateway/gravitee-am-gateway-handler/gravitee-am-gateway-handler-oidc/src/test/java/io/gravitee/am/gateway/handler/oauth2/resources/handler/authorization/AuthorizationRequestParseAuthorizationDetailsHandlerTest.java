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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailsValidator;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.oauth2.Parameters.AUTHORIZATION_DETAILS;
import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_DETAILS_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_OBJECT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_PARAMETERS_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationRequestParseAuthorizationDetailsHandlerTest {

    private static final String AUTHORIZATION_DETAILS_VALUE = "[{\"type\":\"account_information\"}]";

    @Mock
    private AuthorizationDetailsValidator authorizationDetailsValidator;
    @Mock
    private RoutingContext context;
    @Mock
    private HttpServerRequest request;

    private AuthorizationRequestParseAuthorizationDetailsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthorizationRequestParseAuthorizationDetailsHandler(authorizationDetailsValidator);
        when(context.request()).thenReturn(request);
        lenient().when(context.get(anyString())).thenReturn(null);
    }

    @Test
    void shouldContinueWhenTheRequestHasNoAuthorizationDetails() {
        handler.handle(context);

        verify(context).next();
        verifyNoInteractions(authorizationDetailsValidator);
        verify(context, never()).put(anyString(), any());
    }

    @Test
    void shouldKeepTheValidatedAuthorizationDetails() {
        Client client = new Client();
        List<Map<String, Object>> validated = List.of(Map.of("type", "account_information"));
        when(request.getParam(AUTHORIZATION_DETAILS)).thenReturn(AUTHORIZATION_DETAILS_VALUE);
        when(context.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(authorizationDetailsValidator.validate(AUTHORIZATION_DETAILS_VALUE, client)).thenReturn(validated);

        handler.handle(context);

        verify(context).put(AUTHORIZATION_DETAILS_CONTEXT_KEY, validated);
        verify(context).next();
    }

    @Test
    void shouldFailWithTheValidationError() {
        Client client = new Client();
        InvalidRequestException error = new InvalidRequestException("authorization_details is not valid JSON");
        when(request.getParam(AUTHORIZATION_DETAILS)).thenReturn(AUTHORIZATION_DETAILS_VALUE);
        when(context.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(authorizationDetailsValidator.validate(AUTHORIZATION_DETAILS_VALUE, client)).thenThrow(error);

        handler.handle(context);

        verify(context).fail(error);
        verify(context, never()).next();
    }

    @Test
    void shouldLeaveARequestObjectAlone() {
        when(request.getParam(AUTHORIZATION_DETAILS)).thenReturn(AUTHORIZATION_DETAILS_VALUE);
        when(context.get(REQUEST_OBJECT_KEY)).thenReturn(new Object());

        handler.handle(context);

        verify(context).next();
        verifyNoInteractions(authorizationDetailsValidator);
    }

    @Test
    void shouldLeavePushedParametersAlone() {
        AuthenticationFlowContext authFlowContext = new AuthenticationFlowContext();
        Map<String, Object> data = new HashMap<>();
        data.put(REQUEST_PARAMETERS_KEY, Map.of());
        authFlowContext.setData(data);
        when(request.getParam(AUTHORIZATION_DETAILS)).thenReturn(AUTHORIZATION_DETAILS_VALUE);
        when(context.get(AUTH_FLOW_CONTEXT_KEY)).thenReturn(authFlowContext);

        handler.handle(context);

        verify(context).next();
        verifyNoInteractions(authorizationDetailsValidator);
    }
}
