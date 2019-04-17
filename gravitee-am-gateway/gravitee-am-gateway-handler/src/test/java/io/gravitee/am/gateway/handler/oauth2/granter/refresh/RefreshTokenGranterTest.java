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
package io.gravitee.am.gateway.handler.oauth2.granter.refresh;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.model.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

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
    // TODO
    public void shouldGenerateAnAccessToken() {
       /* String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setAuthorizedGrantTypes(Arrays.asList(new String[]{"refresh_token"}));

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("my-client-id");
        oAuth2Request.setGrantType("refresh_token");

        when(tokenRequest.getClient()).thenReturn("my-client-id");
        when(tokenRequest.getGrantType()).thenReturn("refresh_token");
        when(tokenRequest.getRequestParameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(tokenService.refresh(refreshToken, tokenRequest)).thenReturn(Single.just(new RefreshToken()));
        when(tokenService.create(oAuth2Request, client, null)).thenReturn(Single.just(new AccessToken("token")));

        TestObserver<Token> testObserver = granter.grant(tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken -> "token".equals(accessToken.getValue()));*/
    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidRequest() {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        Client client = new Client();
        client.setClientId("my-client-id");

        when(tokenRequest.getRequestParameters()).thenReturn(parameters);

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

        when(tokenRequest.getRequestParameters()).thenReturn(parameters);

        when(tokenService.refresh(refreshToken, tokenRequest, client)).thenReturn(Single.error(new InvalidGrantException()));

        granter.grant(tokenRequest, client).test().assertError(InvalidGrantException.class);
    }
}
