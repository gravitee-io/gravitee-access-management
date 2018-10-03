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
package io.gravitee.am.gateway.handler.oauth2.token;

import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenEnhancerImpl;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        // no openid scope for the request

        Client client = new Client();

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getAdditionalInformation().isEmpty());
    }

    @Test
    public void shouldEnhanceToken_withIDToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        String idTokenPayload = "payload";

        when(idTokenService.create(any(), any(), any())).thenReturn(Single.just(idTokenPayload));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getAdditionalInformation().containsKey("id_token"));

        verify(idTokenService, times(1)).create(any(), any(), any());
    }
}
