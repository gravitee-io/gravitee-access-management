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
package io.gravitee.am.gateway.handler.oauth2.service.granter.ciba;

import io.gravitee.am.common.ciba.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CibaTokenGranterTest {

    @Mock
    private TokenRequestResolver tokenRequestResolver;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private AuthenticationRequestService authenticationRequestService;

    @Mock
    private TokenRequest tokenRequest;

    @Mock
    private Domain domain;

    @Mock
    private RulesEngine rulesEngine;

    private CibaTokenGranter granter;

    @Before
    public void init() {
        reset(tokenRequestResolver, tokenService, userAuthenticationManager, tokenRequest, authenticationRequestService);
        granter = new CibaTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager, authenticationRequestService, domain, rulesEngine);
    }

    @Test
    public void shouldRejectRequest_MissingAuthReqId() {
        when(tokenRequest.parameters()).thenReturn(new LinkedMultiValueMap<>());

        final TestObserver<Token> test = granter.grant(tokenRequest, mock(Client.class)).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertError(InvalidRequestException.class);
    }

    @Test
    public void shouldRejectRequest_UnknownAuthReqId() {
        final LinkedMultiValueMap<String, String> value = new LinkedMultiValueMap<>();
        value.add(Parameters.AUTH_REQ_ID, "unknown_req_id");

        when(tokenRequest.parameters()).thenReturn(value);
        when(authenticationRequestService.retrieve(any(), any())).thenReturn(Single.error(new AuthenticationRequestNotFoundException("unknown_req_id")));

        final TestObserver<Token> test = granter.grant(tokenRequest, mock(Client.class)).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertError(AuthenticationRequestNotFoundException.class);
    }

    @Test
    public void shouldRejectRequest_SlowDown() {
        final LinkedMultiValueMap<String, String> value = new LinkedMultiValueMap<>();
        value.add(Parameters.AUTH_REQ_ID, "slow_down");

        when(tokenRequest.parameters()).thenReturn(value);
        when(authenticationRequestService.retrieve(any(), any())).thenReturn(Single.error(new SlowDownException()));

        final TestObserver<Token> test = granter.grant(tokenRequest, mock(Client.class)).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertError(SlowDownException.class);
    }

}
