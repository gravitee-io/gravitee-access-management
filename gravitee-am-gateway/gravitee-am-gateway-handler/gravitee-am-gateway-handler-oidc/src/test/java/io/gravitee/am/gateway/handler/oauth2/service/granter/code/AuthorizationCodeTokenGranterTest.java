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
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
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


    private AuthorizationCodeTokenGranter granter;

    @Before
    public void init() {
        Mockito.when(env.getProperty(any(), Mockito.eq(Boolean.class), any())).thenReturn(true);
        granter = new AuthorizationCodeTokenGranter(tokenRequestResolver, tokenService, authorizationCodeService, userAuthenticationManager, authenticationFlowContextService, env, rulesEngine);
    }

    @Test
    public void the_exception_message_should_be_propagated_to_granter() {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setRequestParameters(new LinkedMultiValueMap<>());
        Mockito.when(authorizationCodeService.remove(any(), any())).thenReturn(Maybe.just(authorizationCode));
        Mockito.when(authenticationFlowContextService.removeContext(any(), Mockito.anyInt())).thenReturn(Single.just(new AuthenticationFlowContext()));
        Mockito.when(userAuthenticationManager.loadPreAuthenticatedUser(any(), any())).thenReturn(Maybe.error(new RuntimeException("unknown error")));
        TokenRequest tokenRequest = Mockito.mock();

        LinkedMultiValueMap values = new LinkedMultiValueMap<>();
        when(tokenRequest.parameters()).thenReturn(values);
        values.add(Parameters.CODE, "xxxxx");

        final TestObserver<Token> test = granter.grant(tokenRequest, mock(Client.class)).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertError(ex -> ex.getMessage().equals("unknown error"));
    }

}