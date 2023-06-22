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
package io.gravitee.am.gateway.handler.oauth2.service.granter.refresh;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RefreshTokenGranterTest {

    @InjectMocks
    private RefreshTokenGranter granter = new RefreshTokenGranter();

    @Mock
    private TokenRequest tokenRequest;

    @Mock
    private TokenService tokenService;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ExecutionContext executionContext;

    @Test
    public void shouldGenerateAnAccessToken() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.just(new RefreshToken(refreshToken)));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getValue().equals("test-token"));
    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidRequest() {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        Client client = new Client();
        client.setClientId("my-client-id");

        when(tokenRequest.parameters()).thenReturn(parameters);

        granter.grant(tokenRequest, client).test().assertError(InvalidRequestException.class);
    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidGrant() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));

        when(tokenRequest.parameters()).thenReturn(parameters);

        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.error(new InvalidGrantException()));

        granter.grant(tokenRequest, client).test().assertError(InvalidGrantException.class);
    }

    @Test
    public void shouldGenerateAnAccessToken_DisableRefreshTokenRotation() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));
        client.setDisableRefreshTokenRotation(true);

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(client.getClientId());
        oAuth2Request.setGrantType(GrantType.REFRESH_TOKEN);
        oAuth2Request.setParameters(parameters);

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        ArgumentCaptor<OAuth2Request> oAuth2RequestArgumentCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
        when(tokenService.create(oAuth2RequestArgumentCaptor.capture(), any(), any())).thenReturn(Single.just(accessToken));
        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.just(new RefreshToken(refreshToken)));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getValue().equals("test-token"));
        testObserver.assertValue(token -> token.getRefreshToken().equals(refreshToken));
        OAuth2Request oAuth2RequestArgumentCaptorValue = oAuth2RequestArgumentCaptor.getValue();
        assertNotNull(oAuth2RequestArgumentCaptorValue);
        assertFalse(oAuth2RequestArgumentCaptorValue.isSupportRefreshToken());
    }
}
