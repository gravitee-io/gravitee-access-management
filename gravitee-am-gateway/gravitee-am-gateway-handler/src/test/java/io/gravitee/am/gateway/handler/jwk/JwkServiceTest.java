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
package io.gravitee.am.gateway.handler.jwk;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.gravitee.am.gateway.handler.jwk.impl.JwkServiceImpl;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JwkServiceTest {

    private static final String JWKS_URI = "http://client/jwk/uri";

    @InjectMocks
    private JwkService jwkService = new JwkServiceImpl();

    @Mock
    public WebClient client;

    @Test
    public void testGetKeys_UriException() {
        TestObserver testObserver = jwkService.getKeys("blabla").test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys_errorResponse() {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);


        when(client.getAbs(any())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys_paseException() {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);


        when(client.getAbs(any())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.bodyAsString()).thenReturn("{\"unknown\":[]}");

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys() throws JsonProcessingException {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        String bodyAsString = "{\"keys\":[{\"kty\": \"RSA\",\"use\": \"enc\",\"kid\": \"KID\",\"n\": \"modulus\",\"e\": \"exponent\"}]}";

        when(client.getAbs(any())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.bodyAsString()).thenReturn(bodyAsString);

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void testGetKey_noKid() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        TestObserver testObserver = jwkService.getKey(jwkSet,null).test();
    }

    @Test
    public void testGetKey_noKFound() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        when(jwk.getKid()).thenReturn("notTheExpectedOne");

        TestObserver testObserver = jwkService.getKey(jwkSet,"expectedKid").test();
    }

    @Test
    public void testGetKey_ok() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        when(jwk.getKid()).thenReturn("expectedKid");

        TestObserver testObserver = jwkService.getKey(jwkSet,"expectedKid").test();
    }
}
