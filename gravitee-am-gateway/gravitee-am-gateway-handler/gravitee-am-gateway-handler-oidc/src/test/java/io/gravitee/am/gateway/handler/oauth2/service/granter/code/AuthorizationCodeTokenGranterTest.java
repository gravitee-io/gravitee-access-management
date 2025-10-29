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
package io.gravitee.am.gateway.handler.oauth2.service.granter.code;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.validation.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.validation.ResourceConsistencyValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.reactivex.rxjava3.core.Completable;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationCodeTokenGranterTest {


    @Mock
    private TokenRequestResolver tokenRequestResolver;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthorizationCodeService authorizationCodeService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Mock
    private Environment env;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    private AuthorizationCodeTokenGranter granter;

    @Before
    public void init() {
        Mockito.when(env.getProperty(any(), Mockito.eq(Boolean.class), any())).thenReturn(true);
        granter = new AuthorizationCodeTokenGranter(tokenRequestResolver, tokenService, authorizationCodeService, userAuthenticationManager, authenticationFlowContextService, resourceConsistencyValidationService, env, rulesEngine);
    }

    @Test
    public void shouldPropagateExceptionMessageToGranter() {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setRequestParameters(new LinkedMultiValueMap<>());
        Mockito.when(authorizationCodeService.remove(any(), any())).thenReturn(Maybe.just(authorizationCode));
        Mockito.when(authenticationFlowContextService.removeContext(any(), Mockito.anyInt())).thenReturn(Maybe.just(new AuthenticationFlowContext()));
        Mockito.when(userAuthenticationManager.loadPreAuthenticatedUser(any(), any())).thenReturn(Maybe.error(new RuntimeException("unknown error")));
        Mockito.when(resourceConsistencyValidationService.validateConsistency(any(), any())).thenReturn(Completable.complete());
        TokenRequest tokenRequest = Mockito.mock();

        LinkedMultiValueMap values = new LinkedMultiValueMap<>();
        when(tokenRequest.parameters()).thenReturn(values);
        values.add(Parameters.CODE, "xxxxx");

        final TestObserver<Token> test = granter.grant(tokenRequest, mock(Client.class)).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertError(ex -> ex.getMessage().equals("unknown error"));
    }

    // RFC 8707 Resource Consistency Tests

    @Test
    public void shouldPropagateInvalidResourceExceptionWhenValidationFails() {
        // Arrange: Token request with resources that fail validation
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setRequestParameters(new LinkedMultiValueMap<>());
        Set<String> authResources = new HashSet<>(Arrays.asList("https://api.example.com/photos"));
        authorizationCode.setResources(authResources);

        Set<String> tokenResources = new HashSet<>(Arrays.asList("https://api.example.com/unknown"));
        
        TokenRequest tokenRequest = mock(TokenRequest.class);
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.CODE, "test-code");
        Mockito.when(tokenRequest.parameters()).thenReturn(parameters);
        lenient().when(tokenRequest.getResources()).thenReturn(tokenResources);

        Client client = mock(Client.class);

        Mockito.when(authorizationCodeService.remove(any(), any())).thenReturn(Maybe.just(authorizationCode));
        Mockito.when(authenticationFlowContextService.removeContext(any(), Mockito.anyInt())).thenReturn(Maybe.just(new AuthenticationFlowContext()));
        Mockito.when(resourceConsistencyValidationService.validateConsistency(any(TokenRequest.class), eq(authResources)))
                .thenReturn(Completable.error(new InvalidResourceException("The requested resource is not recognized")));

        // Act
        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);

        // Assert - verify that InvalidResourceException is propagated
        testObserver.assertError(InvalidResourceException.class);
        testObserver.assertError(ex -> ex.getMessage().contains("not recognized"));
    }

}