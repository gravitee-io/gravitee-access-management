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

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.model.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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
    private OAuth2Request oAuth2Request;

    @Mock
    private ClientService clientService;

    @Mock
    private TokenService tokenService;

    @Test
    public void shouldGenerateAnAccessToken() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        when(tokenRequest.getClientId()).thenReturn("my-client-id");
        when(tokenRequest.getGrantType()).thenReturn("refresh_token");
        when(tokenRequest.getRequestParameters()).thenReturn(parameters);

        when(oAuth2Request.getClientId()).thenReturn("my-client-id");
        when(oAuth2Request.getGrantType()).thenReturn("refresh_token");
        when(oAuth2Request.getRequestParameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(clientService.findByClientId("my-client-id")).thenReturn(Maybe.just(new Client()));
        when(tokenService.refresh(refreshToken, oAuth2Request)).thenReturn(Single.just(new DefaultAccessToken("token")));

        TestObserver<AccessToken> testObserver = granter.grant(tokenRequest).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken -> "token".equals(accessToken.getValue()));

    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidRequest() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        when(tokenRequest.getClientId()).thenReturn("my-client-id");
        when(tokenRequest.getGrantType()).thenReturn("refresh_token");
        when(tokenRequest.getRequestParameters()).thenReturn(parameters);

        when(oAuth2Request.getClientId()).thenReturn("my-client-id");
        when(oAuth2Request.getGrantType()).thenReturn("refresh_token");
        when(oAuth2Request.getRequestParameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(clientService.findByClientId("my-client-id")).thenReturn(Maybe.just(new Client()));
        when(tokenService.refresh(refreshToken, oAuth2Request)).thenReturn(Single.just(new DefaultAccessToken("token")));

        TestObserver<AccessToken> testObserver = granter.grant(tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestException.class);

    }

    @Test
    public void shouldNotGenerateAnAccessToken_invalidGrant() {
        String refreshToken = "refresh-token";
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set("refresh_token", refreshToken);

        when(tokenRequest.getClientId()).thenReturn("my-client-id");
        when(tokenRequest.getGrantType()).thenReturn("refresh_token");
        when(tokenRequest.getRequestParameters()).thenReturn(parameters);

        when(oAuth2Request.getClientId()).thenReturn("my-client-id");
        when(oAuth2Request.getGrantType()).thenReturn("refresh_token");
        when(oAuth2Request.getRequestParameters()).thenReturn(parameters);
        when(tokenRequest.createOAuth2Request()).thenReturn(oAuth2Request);

        when(clientService.findByClientId("my-client-id")).thenReturn(Maybe.just(new Client()));
        when(tokenService.refresh(refreshToken, oAuth2Request)).thenReturn(Single.error(new InvalidGrantException()));

        TestObserver<AccessToken> testObserver = granter.grant(tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);
    }
}
