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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenEnhancerImpl;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenEnhancerTest {

    @InjectMocks
    private TokenEnhancer tokenEnhancer = new TokenEnhancerImpl();

    @Mock
    private IDTokenService idTokenService;

    @Test
    public void shouldEnhanceToken_withoutIDToken() {
        var user = new User();
        var id = "user-id";
        user.setId(id);
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setSubject(id);
        // no openid scope for the request
        Client client = new Client();
        Token accessToken = new AccessToken("token-id");

        tokenEnhancer.enhance(accessToken, oAuth2Request, client, user, null)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(accessToken1 -> accessToken1.getAdditionalInformation().isEmpty());
        verify(idTokenService, never()).create(any(), any(), any(), any());
    }

    @Test
    public void shouldEnhanceTokenThrowErrorWhenNoUserWithOpenIdScope() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        Client client = new Client();
        Token accessToken = new AccessToken("token-id");

        tokenEnhancer.enhance(accessToken, oAuth2Request, client, null, null)
                .test()
                .assertNotComplete()
                .assertError(InvalidScopeException.class);
        verify(idTokenService, never()).create(any(), any(), any(), any());
    }

    @Test
    public void shouldEnhanceToken_withIDToken() {
        var user = new User();
        var id = "user-id";
        user.setId(id);
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setSubject(id);
        oAuth2Request.setScopes(Collections.singleton("openid"));
        Client client = new Client();
        Token accessToken = new AccessToken("token-id");
        String idTokenPayload = "payload";
        when(idTokenService.create(oAuth2Request, client, user, null)).thenReturn(Single.just(idTokenPayload));

        tokenEnhancer.enhance(accessToken, oAuth2Request, client, user, null)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(accessToken1 -> accessToken1.getAdditionalInformation().containsKey("id_token"));
        verify(idTokenService, times(1)).create(any(), any(), any(), any());
    }
}
