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
package io.gravitee.am.gateway.handler.oauth2.service.par;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.par.impl.PushedAuthorizationRequestServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PushedAuthorizationRequestServiceTest {

    @InjectMocks
    private PushedAuthorizationRequestServiceImpl cut ;

    @Mock
    private PushedAuthorizationRequestRepository repository;

    @Mock
    private Domain domain;

    @Mock
    private JWSService jwsService;

    @Mock
    private JWEService jweService;

    @Mock
    private JWKService jwkService;

    @Test
    public void shouldNotPersist_ClientIdMismatch() {
        final Client client = new Client();
        client.setClientId("clientid");

        final PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("scope", "openid");
        parameters.add("response_type", "code");
        parameters.add("client_id", "otherid");
        par.setParameters(parameters);

        final TestObserver<PushedAuthorizationRequestResponse> observer = cut.registerParameters(par, client).test();

        observer.awaitTerminalEvent();
        observer.assertError(InvalidRequestException.class);
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldPersist_ParametersWithoutRequest() {
        final Client client = createClient();

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("scope", "openid");
        parameters.add("response_type", "code");
        parameters.add("client_id", client.getClientId());

        final PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setParameters(parameters);
        par.setId("parid");
        par.setClient(client.getId());

        when(repository.create(any())).thenReturn(Single.just(par));

        final TestObserver<PushedAuthorizationRequestResponse> observer = cut.registerParameters(par, client).test();

        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        observer.assertValue(parr -> parr.getExp() > 0 && parr.getRequestUri().equals(PushedAuthorizationRequestService.PAR_URN_PREFIX+par.getId()));

        verify(repository).create(any());
    }

    @Test
    public void shouldNotPersist_RequestUriPresent() {
        final Client client = createClient();

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("request_uri", "urn:toto");
        parameters.add("client_id", client.getClientId());

        final PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setParameters(parameters);
        par.setId("parid");
        par.setClient(client.getId());

        final TestObserver<PushedAuthorizationRequestResponse> observer = cut.registerParameters(par, client).test();

        observer.awaitTerminalEvent();
        observer.assertFailure(InvalidRequestException.class);

        verify(repository, never()).create(any());
    }

    @Test
    public void shouldNotPersist_RequestMalformed() {
        final Client client = createClient();

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("request", "invalid json object");
        parameters.add("client_id", client.getClientId());

        final PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setParameters(parameters);
        par.setId("parid");
        par.setClient(client.getId());

        when(jweService.decrypt(any(), any())).thenReturn(Single.error(new ParseException("parse error",1)));

        final TestObserver<PushedAuthorizationRequestResponse> observer = cut.registerParameters(par, client).test();

        observer.awaitTerminalEvent();
        observer.assertFailure(InvalidRequestObjectException.class);

        verify(repository, never()).create(any());
    }

    @Test
    public void shouldNotPersist_RequestWithUnexpectedClaims() throws Exception {
        final Client client = createClient();

        final String jwtString = "eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOlsiMTIzNDU2Nzg5MCIsImh0dHA6Ly9vcCJdLCJjbGllbnRfaWQiOiJjbGllbnRpZCIsInJlcXVlc3QiOiJkc2Zkc2YifQ.S6EQZgosP7FlIfyiV85bjeWnEW4yGjf8PlAZiYIZkyIgiHzlFIEnisxc_P42dKcFK8azW6xVw7OiOYLoIEo2QhZqvT4YZWgAjlqZoaMzBs68zkQr10xXrMLK8k-6wsQUONy49f7cR5niauuKYMgeVc4k5qLDvc6p1iKfUZu6VVvv-nhNT3GOacgJqwviofI-ZvBGGr0O8kP13nWf5RRElNgNw06Hnza139KwqEsim7kFDzs9TCrXl-3CzYvYtF-VYTsDTLf9ArkJgsxvs1PSULu0Sq9m5_sokJuV3DiF9daj2v3Zmd0ZYRbr1OSKreseW0fxNGmQZyHaVgtEowUv8g";
        final JWT parse = JWTParser.parse(jwtString);

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("request", jwtString);
        parameters.add("client_id", client.getClientId());

        final PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setParameters(parameters);
        par.setId("parid");
        par.setClient(client.getId());

        when(jweService.decrypt(any(), any())).thenReturn(Single.just(parse));

        final TestObserver<PushedAuthorizationRequestResponse> observer = cut.registerParameters(par, client).test();

        observer.awaitTerminalEvent();
        observer.assertError(e -> e instanceof InvalidRequestObjectException && e.getMessage().equals("Claims request and request_uri are forbidden"));

        verify(repository, never()).create(any());
    }

    private Client createClient() {
        final Client client = new Client();
        client.setId("cid");
        client.setClientId("clientid");
        return client;
    }

    @Test
    public void shouldNot_ReadFromURI_InvalidURI() {
        final TestObserver<JWT> testObserver = cut.readFromURI("invalideuri", createClient(), new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(InvalidRequestException.class);

        verify(repository, never()).findById(any());
    }

    @Test
    public void shouldReadFromURI_UnknownId() {
        final String ID = "parid";
        final String requestUri = PushedAuthorizationRequestService.PAR_URN_PREFIX + ID;

        when(repository.findById(ID)).thenReturn(Maybe.empty());

        final TestObserver<JWT> testObserver = cut.readFromURI(requestUri, createClient(), new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(InvalidRequestUriException.class);

        verify(repository).findById(eq(ID));
    }

    @Test
    public void shouldReadFromURI_ExpiredPAR() {
        final String ID = "parid";
        final String requestUri = PushedAuthorizationRequestService.PAR_URN_PREFIX + ID;

        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setExpireAt(new Date(Instant.now().minusSeconds(10).toEpochMilli()));

        when(repository.findById(ID)).thenReturn(Maybe.just(par));

        final TestObserver<JWT> testObserver = cut.readFromURI(requestUri, createClient(), new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(InvalidRequestUriException.class);

        verify(repository).findById(eq(ID));
    }


    @Test
    public void shouldReadFromURI_MissingRequest_FAPI() {
        final String ID = "parid";
        final String requestUri = PushedAuthorizationRequestService.PAR_URN_PREFIX + ID;

        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setParameters(new LinkedMultiValueMap<>());
        par.setExpireAt(new Date(Instant.now().plusSeconds(10).toEpochMilli()));

        when(domain.usePlainFapiProfile()).thenReturn(true);
        when(repository.findById(ID)).thenReturn(Maybe.just(par));

        final TestObserver<JWT> testObserver = cut.readFromURI(requestUri, createClient(), new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(InvalidRequestException.class);

        verify(repository).findById(eq(ID));
    }

    @Test
    public void shouldReadFromURI_MissingRequestParameter() {
        // in this test, Plain JWT is created using the set of parameters
        final String ID = "parid";
        final String requestUri = PushedAuthorizationRequestService.PAR_URN_PREFIX + ID;

        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setExpireAt(new Date(Instant.now().plusSeconds(10).toEpochMilli()));

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("key1", "value1");
        parameters.add("key2", "value2");
        par.setParameters(parameters);

        when(repository.findById(ID)).thenReturn(Maybe.just(par));

        final TestObserver<JWT> testObserver = cut.readFromURI(requestUri, createClient(), new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwt ->
                jwt.getJWTClaimsSet().getStringClaim("key1") != null &&
                jwt.getJWTClaimsSet().getStringClaim("key2") != null);

        verify(repository).findById(eq(ID));
    }


    @Test
    public void shouldReadFromURI_RequestParameter() throws Exception {
        // in this test, Plain JWT is created using the set of parameters
        final String ID = "parid";
        final String requestUri = PushedAuthorizationRequestService.PAR_URN_PREFIX + ID;

        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setExpireAt(new Date(Instant.now().plusSeconds(10).toEpochMilli()));

        final LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REQUEST, "some-jwt");
        par.setParameters(parameters);

        when(repository.findById(ID)).thenReturn(Maybe.just(par));

        JWTClaimsSet claimsSet = new JWTClaimsSet
                .Builder()
                .claim(Claims.aud, "https://op/domain/oidc")
                .claim(Claims.scope, "openid")
                .claim(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE, "code")
                .build();
        final SignedJWT signedJwt = mock(SignedJWT.class);
        when(signedJwt.getJWTClaimsSet()).thenReturn(claimsSet);
        when(jweService.decrypt(any(), any())).thenReturn(Single.just(signedJwt));
        final JWSHeader jwsHeaders = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        when(signedJwt.getHeader()).thenReturn(jwsHeaders);

        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(mock(JWKSet.class)));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(mock(JWK.class)));
        when( jwsService.isValidSignature(any(), any())).thenReturn(true);

        final Client client = createClient();
        client.setRequestObjectSigningAlg(JWSAlgorithm.RS256.getName());

        final TestObserver<JWT> testObserver = cut.readFromURI(requestUri, client, new OpenIDProviderMetadata()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwt ->
                jwt.getJWTClaimsSet().getStringClaim(Claims.aud) != null &&
                        jwt.getJWTClaimsSet().getStringClaim(Claims.scope) != null);

        verify(repository).findById(eq(ID));
    }
}
