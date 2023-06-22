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
package io.gravitee.am.gateway.handler.oauth2.service.granter.password;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceOwnerPasswordCredentialsTokenGranterTest {

    @InjectMocks
    private ResourceOwnerPasswordCredentialsTokenGranter granter;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private TokenRequest tokenRequest;

    @Mock
    private TokenRequestResolver tokenRequestResolver;

    @Mock
    private TokenService tokenService;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ExecutionContext executionContext;

    @Before
    public void setUp() {
        granter.setUserAuthenticationManager(userAuthenticationManager);
    }

    @Test
    public void shouldGenerateAnAccessToken() {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(Parameters.USERNAME, "my-username");
        parameters.set(Parameters.PASSWORD, "my-password");

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"password"}));

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(new OAuth2Request());

        when(tokenRequestResolver.resolve(any(), any(), any())).thenReturn(Single.just(tokenRequest));
        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(userAuthenticationManager.authenticate(any(Client.class), any(Authentication.class))).thenReturn(Single.just(new User()));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));
        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getValue().equals("test-token"));

    }

}
