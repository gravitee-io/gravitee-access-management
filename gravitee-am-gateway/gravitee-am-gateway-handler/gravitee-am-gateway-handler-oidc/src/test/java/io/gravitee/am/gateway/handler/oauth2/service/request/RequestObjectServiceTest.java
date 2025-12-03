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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.gateway.handler.oidc.service.request.impl.RequestObjectServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import net.minidev.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestObjectServiceTest {

    @InjectMocks
    private RequestObjectService requestObjectService = new RequestObjectServiceImpl();

    @Mock
    private JWEService jweService;

    @Mock
    private Domain domain;

    @Mock
    private WebClient webClient;

    @Test
    public void shouldNotReadRequestObject_plainJwt() {
        Client client = new Client();
        String request = "request-object";
        PlainJWT plainJWT = mock(PlainJWT.class);;

        when(jweService.decrypt(request,client, false)).thenReturn(Single.just(plainJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObject(request, client, false).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotReadRequestObject_algo_none() throws ParseException {
        Client client = new Client();
        String request = "request-object";
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse("NONE"));
        JSONObject jsonObject = new JSONObject();
        SignedJWT signedJWT = new SignedJWT(jwsHeader,  JWTClaimsSet.parse(jsonObject));

        when(jweService.decrypt(request,client,  false)).thenReturn(Single.just(signedJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObject(request, client, false).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldReadRequestObjectFromURI_URL_notRestricted() throws ParseException {
        Client client = new Client();
        String request = "https://somewhere";

        final OIDCSettings oidcDomainSettings = new OIDCSettings();
        oidcDomainSettings.setRequestUris(null);
        when(domain.getOidc()).thenReturn(oidcDomainSettings);

        final HttpRequest httpRequest = mock(HttpRequest.class);
        final HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.bodyAsString()).thenReturn("request_uri_payload");
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(webClient.getAbs(anyString())).thenReturn(httpRequest);

        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse("NONE"));
        JSONObject jsonObject = new JSONObject();
        SignedJWT signedJWT = new SignedJWT(jwsHeader,  JWTClaimsSet.parse(jsonObject));

        when(jweService.decrypt(anyString(),any(), anyBoolean())).thenReturn(Single.just(signedJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObjectFromURI(request, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        // JSON return is invalid but the test is a success as we want to check
        // the call to the Http service target by the request_uri
        testObserver.assertError(InvalidRequestObjectException.class);

        verify(webClient).getAbs(anyString());
        verify(jweService).decrypt(argThat(json -> json.equals("request_uri_payload")),any(),anyBoolean());
    }


    @Test
    public void shouldReadRequestObjectFromURI_URL_restrictedAtDomainLevel() throws ParseException {
        Client client = new Client();
        String request = "https://somewhere";

        final OIDCSettings oidcDomainSettings = new OIDCSettings();
        oidcDomainSettings.setRequestUris(List.of("https://authorizedUrls"));
        when(domain.getOidc()).thenReturn(oidcDomainSettings);

        TestObserver<JWT> testObserver = requestObjectService.readRequestObjectFromURI(request, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidRequestUriException.class);

        verify(webClient, never()).getAbs(anyString());
        verify(jweService, never()).decrypt(any(),anyBoolean());
    }

    @Test
    public void shouldReadRequestObjectFromURI_URL_restrictedAtAppLevel() throws ParseException {
        Client client = new Client();
        client.setRequestUris(List.of("http://authUris"));
        String request = "https://somewhere";

        final OIDCSettings oidcDomainSettings = new OIDCSettings();
        oidcDomainSettings.setRequestUris(List.of("https://somewhere"));
        when(domain.getOidc()).thenReturn(oidcDomainSettings);

        TestObserver<JWT> testObserver = requestObjectService.readRequestObjectFromURI(request, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidRequestUriException.class);

        verify(webClient, never()).getAbs(anyString());
        verify(jweService, never()).decrypt(any(),anyBoolean());
    }


    @Test
    public void shouldReadRequestObjectFromURI_URL_allowedUrl() throws ParseException {
        Client client = new Client();
        String request = "https://somewhere/uri?param=value";

        final OIDCSettings oidcDomainSettings = new OIDCSettings();
        oidcDomainSettings.setRequestUris(List.of("https://somewhere"));
        when(domain.getOidc()).thenReturn(oidcDomainSettings);

        final HttpRequest httpRequest = mock(HttpRequest.class);
        final HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.bodyAsString()).thenReturn("request_uri_payload");
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(webClient.getAbs(anyString())).thenReturn(httpRequest);

        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse("NONE"));
        JSONObject jsonObject = new JSONObject();
        SignedJWT signedJWT = new SignedJWT(jwsHeader,  JWTClaimsSet.parse(jsonObject));

        when(jweService.decrypt(anyString(),any(), anyBoolean())).thenReturn(Single.just(signedJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObjectFromURI(request, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        // JSON return is invalid but the test is a success as we want to check
        // the call to the Http service target by the request_uri
        testObserver.assertError(InvalidRequestObjectException.class);

        verify(webClient).getAbs(anyString());
        verify(jweService).decrypt(argThat(json -> json.equals("request_uri_payload")),any(), anyBoolean());
    }
}
