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
package io.gravitee.am.service.jwk.impl;

import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WebClientJWKSetFetcherTest {

    private static final String JWKS_URI = "http://client/jwk/uri";

    @Mock
    private WebClient webClient;

    private WebClientJWKSetFetcher fetcher;

    @Before
    public void setUp() {
        fetcher = new WebClientJWKSetFetcher(webClient);
    }

    @Test
    public void getKeys_invalidUriString_returnsInvalidClientMetadataException() {
        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys("blabla").test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        verifyNoInteractions(webClient);
    }

    @Test
    public void getKeys_nonOkStatus_returnsInvalidClientMetadataException() {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(404);
        when(response.bodyAsString()).thenReturn("not found");

        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void getKeys_nullBody_returnsInvalidClientMetadataException() {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);

        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void getKeys_unparseableBody_returnsInvalidClientMetadataException() {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsString()).thenReturn("{\"unknown\":[]}");

        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void getKeys_validBody_returnsJWKSet() {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        String bodyAsString = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"enc\",\"kid\":\"KID\",\"n\":\"modulus\",\"e\":\"exponent\"}]}";

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsString()).thenReturn(bodyAsString);

        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys(JWKS_URI).test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(r -> r.jwkSet().getKeys().size() == 1
                && "KID".equals(r.jwkSet().getKeys().get(0).getKid()));
    }

    @Test
    public void getKeys_emptyKeysArray_emitsJWKSetWithNoKeys() {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsString()).thenReturn("{\"keys\":[]}");

        TestObserver<JWKSetFetchResponse> testObserver = fetcher.getKeys(JWKS_URI).test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(r -> r.jwkSet().getKeys() != null && r.jwkSet().getKeys().isEmpty());
    }
}
