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
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationResponse;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.gateway.handler.oidc.service.request.impl.RequestObjectServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import io.gravitee.am.repository.oidc.model.RequestObject;
import io.reactivex.rxjava3.core.Maybe;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Date;
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
    private JWSService jwsService;

    @Mock
    private JWKService jwkService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private RequestObjectRepository requestObjectRepository;

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

    // --- registerRequestObject tests ---

    @Test
    public void shouldNotRegisterRequestObject_parseError() {
        Client client = new Client();
        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest("not-a-valid-jwt");

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotRegisterRequestObject_plainJwt() throws Exception {
        Client client = new Client();
        PlainJWT plainJWT = new PlainJWT(new JWTClaimsSet.Builder().subject("test").build());

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(plainJWT.serialize());

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotRegisterRequestObject_keyNotInClientJwks() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        SignedJWT signedJWT = buildSignedJWT("jwt-key-id", JWSAlgorithm.RS256, (RSAPrivateKey) keyPair.getPrivate());

        io.gravitee.am.model.jose.RSAKey rsaKey = new io.gravitee.am.model.jose.RSAKey();
        rsaKey.setKid("different-key-id");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(rsaKey));

        Client client = new Client();
        client.setJwks(jwkSet);
        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(jwkSet));

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(signedJWT.serialize());

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestException.class);
    }

    @Test
    public void shouldNotRegisterRequestObject_noKeysFromJwkService() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        SignedJWT signedJWT = buildSignedJWT("my-key-id", JWSAlgorithm.RS256, (RSAPrivateKey) keyPair.getPrivate());

        io.gravitee.am.model.jose.RSAKey rsaKey = new io.gravitee.am.model.jose.RSAKey();
        rsaKey.setKid("my-key-id");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(rsaKey));

        Client client = new Client();
        client.setJwks(jwkSet);
        client.setRequestObjectSigningAlg(JWSAlgorithm.RS256.getName());

        when(jwkService.getKeys(client)).thenReturn(Maybe.error(new InvalidRequestObjectException("No keys found")));

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(signedJWT.serialize());

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotRegisterRequestObject_algorithmMismatch() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        SignedJWT signedJWT = buildSignedJWT("my-key-id", JWSAlgorithm.RS256, (RSAPrivateKey) keyPair.getPrivate());

        io.gravitee.am.model.jose.RSAKey rsaKey = new io.gravitee.am.model.jose.RSAKey();
        rsaKey.setKid("my-key-id");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(rsaKey));
        io.gravitee.am.model.jose.JWK jwk = mock(io.gravitee.am.model.jose.JWK.class);

        Client client = new Client();
        client.setJwks(jwkSet);
        client.setRequestObjectSigningAlg("PS256");

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(jwkSet));
        when(jwkService.getKey(any(), anyString())).thenReturn(Maybe.just(jwk));

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(signedJWT.serialize());

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotRegisterRequestObject_invalidSignature() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        SignedJWT signedJWT = buildSignedJWT("my-key-id", JWSAlgorithm.RS256, (RSAPrivateKey) keyPair.getPrivate());

        io.gravitee.am.model.jose.RSAKey rsaKey = new io.gravitee.am.model.jose.RSAKey();
        rsaKey.setKid("my-key-id");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(rsaKey));
        io.gravitee.am.model.jose.JWK jwk = mock(io.gravitee.am.model.jose.JWK.class);

        Client client = new Client();
        client.setJwks(jwkSet);
        client.setRequestObjectSigningAlg(JWSAlgorithm.RS256.getName());

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(jwkSet));
        when(jwkService.getKey(any(), anyString())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(false);

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(signedJWT.serialize());

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldRegisterRequestObject() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        SignedJWT signedJWT = buildSignedJWT("my-key-id", JWSAlgorithm.RS256, (RSAPrivateKey) keyPair.getPrivate());

        io.gravitee.am.model.jose.RSAKey rsaKey = new io.gravitee.am.model.jose.RSAKey();
        rsaKey.setKid("my-key-id");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(rsaKey));
        io.gravitee.am.model.jose.JWK jwk = mock(io.gravitee.am.model.jose.JWK.class);

        Client client = new Client();
        client.setClientId("my-client");
        client.setJwks(jwkSet);
        client.setRequestObjectSigningAlg(JWSAlgorithm.RS256.getName());

        RequestObject storedObject = new RequestObject();
        storedObject.setId("stored-obj-id");
        storedObject.setExpireAt(new Date(System.currentTimeMillis() + 86400_000L));

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(jwkSet));
        when(jwkService.getKey(any(), anyString())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);
        when(requestObjectRepository.create(any())).thenReturn(Single.just(storedObject));
        when(openIDDiscoveryService.getIssuer(any())).thenReturn("https://issuer.example.com");

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(signedJWT.serialize());
        request.setOrigin("https://issuer.example.com");

        TestObserver<RequestObjectRegistrationResponse> testObserver =
                requestObjectService.registerRequestObject(request, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(response ->
                "my-client".equals(response.getAud())
                        && response.getRequestUri().startsWith("urn:ros:stored-obj-id")
                        && "https://issuer.example.com".equals(response.getIss())
        );
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private static SignedJWT buildSignedJWT(String keyId, JWSAlgorithm algorithm, RSAPrivateKey privateKey) throws Exception {
        JWSHeader header = new JWSHeader.Builder(algorithm).keyID(keyId).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("test").build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT;
    }
}
