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
package io.gravitee.am.gateway.handler.oidc.idtoken;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.idtoken.impl.IDTokenServiceImpl;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.jsonwebtoken.*;
import io.jsonwebtoken.impl.crypto.MacSigner;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.security.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IDTokenServiceTest {

    @InjectMocks
    private IDTokenService idTokenService = new IDTokenServiceImpl();

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private CertificateProvider certificateProvider;

    private JwtBuilder jwtBuilder;

    @Test
    public void shouldCreateIDToken_clientOnly_clientCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("client-certificate");

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        String idTokenPayload = "payload";

        when(certificateProvider.sign(anyString())).thenReturn(Single.just(idTokenPayload));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(certificateProvider));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateProvider, times(1)).sign(anyString());
        verify(certificateManager, times(1)).get(anyString());
    }

    @Test
    public void shouldCreateIDToken_clientOnly_defaultCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        Key key = MacSigner.generateKey();
        jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS512, key);
        ((IDTokenServiceImpl) idTokenService).setJwtBuilder(jwtBuilder);

        when(certificateManager.get(anyString())).thenReturn(Maybe.empty());

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(certificateProvider, never()).sign(anyString());
    }

    @Test
    public void shouldCreateIDToken_withUser_claimsRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"name\":{\"essential\":true}}}"));
        oAuth2Request.setRequestParameters(requestParameters);

        Client client = new Client();

        User user = new User();
        Map<String, Object> additionalInformation  = new HashMap<>();
        additionalInformation.put("sub", "user");
        additionalInformation.put("name", "gravitee user");
        additionalInformation.put("family_name", "gravitee");
        user.setAdditionalInformation(additionalInformation);

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");
        accessToken.setScopes(Collections.singleton("openid"));

        Key key = MacSigner.generateKey();
        JwtParser parser = Jwts.parser().setSigningKey(key);
        jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS512, key);
        ((IDTokenServiceImpl) idTokenService).setJwtBuilder(jwtBuilder);

        when(certificateManager.get(anyString())).thenReturn(Maybe.empty());

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idToken -> {
            Jwt jwt = parser.parse(idToken);
            Map<String, Object> claims = (Map<String, Object>) jwt.getBody();
            return !claims.containsKey("family_name");
        });

        verify(certificateManager, times(1)).get(anyString());
        verify(certificateProvider, never()).sign(anyString());
    }
}
