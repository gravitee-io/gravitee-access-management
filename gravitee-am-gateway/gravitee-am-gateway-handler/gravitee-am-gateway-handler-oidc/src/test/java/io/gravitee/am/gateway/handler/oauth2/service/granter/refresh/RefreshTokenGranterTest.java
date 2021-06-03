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
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

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

        Token accessToken = new AccessToken("test-token");

        when(tokenRequest.parameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(tokenService.create(any(), any(), any())).thenReturn(Single.just(accessToken));
        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.just(new RefreshToken(refreshToken)));

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
}
