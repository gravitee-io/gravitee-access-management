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
package io.gravitee.am.gateway.handler.oidc.service.idtoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.impl.IDTokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
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

    @Mock
    private CertificateProvider defaultCertificateProvider;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private JWTService jwtService;

    @Mock
    private JWEService jweService;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldCreateIDToken_clientOnly_clientIdTokenCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("client-certificate");

        String idTokenPayload = "payload";

        io.gravitee.am.gateway.certificate.CertificateProvider idTokenCert = new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider);
        io.gravitee.am.gateway.certificate.CertificateProvider clientCert = new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider);
        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.just(idTokenCert));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(clientCert));
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).findByAlgorithm(any());
        verify(certificateManager, times(1)).get(anyString());
        verify(certificateManager, times(1)).defaultCertificateProvider();
        verify(jwtService, times(1)).encode(any(), eq(idTokenCert));
    }

    @Test
    public void shouldCreateIDToken_clientOnly_clientCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("client-certificate");

        String idTokenPayload = "payload";

        io.gravitee.am.gateway.certificate.CertificateProvider clientCert = new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider);
        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(clientCert));
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).findByAlgorithm(any());
        verify(certificateManager, times(1)).get(anyString());
        verify(certificateManager, times(1)).defaultCertificateProvider();
        verify(jwtService, times(1)).encode(any(), eq(clientCert));
    }

    @Test
    public void shouldCreateIDToken_clientOnly_defaultCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("certificate-client");

        String idTokenPayload = "payload";

        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.get(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).findByAlgorithm(any());
        verify(certificateManager, times(1)).get(anyString());
        verify(certificateManager, times(1)).defaultCertificateProvider();
        verify(jwtService, times(1)).encode(any(), eq(defaultCert));
    }

    @Test
    public void shouldCreateIDToken_customClaims() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        TokenClaim customClaim = new TokenClaim();
        customClaim.setTokenType(TokenTypeHint.ID_TOKEN);
        customClaim.setClaimName("iss");
        customClaim.setClaimValue("https://custom-iss");

        Client client = new Client();
        client.setCertificate("certificate-client");
        client.setClientId("my-client-id");
        client.setTokenCustomClaims(Arrays.asList(customClaim));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        when(templateEngine.getValue("https://custom-iss", Object.class)).thenReturn("https://custom-iss");
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);

        String idTokenPayload = "payload";
        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.get(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null, executionContext).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        JWT jwt = jwtCaptor.getValue();
        assertNotNull(jwt);
        assertTrue(jwt.get("iss") != null && "https://custom-iss".equals(jwt.get("iss")));
        verify(certificateManager, times(1)).findByAlgorithm(any());
        verify(certificateManager, times(1)).get(anyString());
        verify(certificateManager, times(1)).defaultCertificateProvider();
        verify(jwtService, times(1)).encode(any(), eq(defaultCert));
    }

    @Test
    public void shouldCreateIDToken_clientOnly_defaultCertificate_withEncryption() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("certificate-client");
        client.setIdTokenEncryptedResponseAlg("expectEncryption");

        String idTokenPayload = "payload";

        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.get(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));
        when(jweService.encryptIdToken(anyString(),any())).thenReturn(Single.just("encryptedToken"));
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).findByAlgorithm(any());
        verify(certificateManager, times(1)).get(anyString());
        verify(certificateManager, times(1)).defaultCertificateProvider();
        verify(jwtService, times(1)).encode(any(), eq(defaultCert));
        verify(jweService, times(1)).encryptIdToken(anyString(),any());
    }

    @Test
    public void shouldCreateIDToken_withUser_claimsRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"name\":{\"essential\":true}}}"));
        oAuth2Request.setParameters(requestParameters);

        Client client = new Client();
        client.setCertificate("certid");

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.setExp(expectedJwt.getIat() + 14400);

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));
        ((IDTokenServiceImpl) idTokenService).setObjectMapper(objectMapper);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
    }

    @Test
    public void shouldCreateIDToken_withUser_claimsRequest_acrValues() {
        Client client = new Client();
        User user = createUser();

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}"));
        oAuth2Request.setParameters(requestParameters);

        io.gravitee.am.gateway.certificate.CertificateProvider defaultCert = new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider);

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.get(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCert);
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        ((IDTokenServiceImpl) idTokenService).setObjectMapper(objectMapper);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        ArgumentCaptor<JWT> tokenArgumentCaptor = ArgumentCaptor.forClass(JWT.class);
        verify(jwtService).encode(tokenArgumentCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
        JWT idToken = tokenArgumentCaptor.getValue();
        assertTrue(idToken.containsKey(Claims.acr) && idToken.get(Claims.acr).equals("urn:mace:incommon:iap:silver"));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "profile")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.put(StandardClaims.WEBSITE, user.getAdditionalInformation().get(StandardClaims.WEBSITE));
        expectedJwt.put(StandardClaims.ZONEINFO, user.getAdditionalInformation().get(StandardClaims.ZONEINFO));
        expectedJwt.put(StandardClaims.BIRTHDATE, user.getAdditionalInformation().get(StandardClaims.BIRTHDATE));
        expectedJwt.put(StandardClaims.GENDER, user.getAdditionalInformation().get(StandardClaims.GENDER));
        expectedJwt.put(StandardClaims.PROFILE, user.getAdditionalInformation().get(StandardClaims.PROFILE));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.PREFERRED_USERNAME, user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
        expectedJwt.put(StandardClaims.GIVEN_NAME, user.getAdditionalInformation().get(StandardClaims.GIVEN_NAME));
        expectedJwt.put(StandardClaims.MIDDLE_NAME, user.getAdditionalInformation().get(StandardClaims.MIDDLE_NAME));
        expectedJwt.put(StandardClaims.LOCALE, user.getAdditionalInformation().get(StandardClaims.LOCALE));
        expectedJwt.put(StandardClaims.PICTURE, user.getAdditionalInformation().get(StandardClaims.PICTURE));
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.UPDATED_AT, user.getAdditionalInformation().get(StandardClaims.UPDATED_AT));
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.put(StandardClaims.NICKNAME, user.getAdditionalInformation().get(StandardClaims.NICKNAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.FAMILY_NAME, user.getAdditionalInformation().get(StandardClaims.FAMILY_NAME));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_openid_Legacy() {
        ReflectionTestUtils.setField(idTokenService, "legacyOpenidScope", true);

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.put(StandardClaims.WEBSITE, user.getAdditionalInformation().get(StandardClaims.WEBSITE));
        expectedJwt.put(StandardClaims.ZONEINFO, user.getAdditionalInformation().get(StandardClaims.ZONEINFO));
        expectedJwt.put(StandardClaims.BIRTHDATE, user.getAdditionalInformation().get(StandardClaims.BIRTHDATE));
        expectedJwt.put(StandardClaims.GENDER, user.getAdditionalInformation().get(StandardClaims.GENDER));
        expectedJwt.put(StandardClaims.PROFILE, user.getAdditionalInformation().get(StandardClaims.PROFILE));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.PREFERRED_USERNAME, user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
        expectedJwt.put(StandardClaims.GIVEN_NAME, user.getAdditionalInformation().get(StandardClaims.GIVEN_NAME));
        expectedJwt.put(StandardClaims.MIDDLE_NAME, user.getAdditionalInformation().get(StandardClaims.MIDDLE_NAME));
        expectedJwt.put(StandardClaims.LOCALE, user.getAdditionalInformation().get(StandardClaims.LOCALE));
        expectedJwt.put(StandardClaims.PICTURE, user.getAdditionalInformation().get(StandardClaims.PICTURE));
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.UPDATED_AT, user.getAdditionalInformation().get(StandardClaims.UPDATED_AT));
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.put(StandardClaims.NICKNAME, user.getAdditionalInformation().get(StandardClaims.NICKNAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.FAMILY_NAME, user.getAdditionalInformation().get(StandardClaims.FAMILY_NAME));
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.PHONE_NUMBER, user.getAdditionalInformation().get(StandardClaims.PHONE_NUMBER));
        expectedJwt.put(StandardClaims.PHONE_NUMBER_VERIFIED, user.getAdditionalInformation().get(StandardClaims.PHONE_NUMBER_VERIFIED));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));

        final JWT capturedValue = jwtCaptor.getValue();
        assertEquals(capturedValue.getSub(), expectedJwt.getSub());
        assertEquals(capturedValue.getAud(), expectedJwt.getAud());
        assertEquals(capturedValue.get(StandardClaims.ADDRESS), expectedJwt.get(StandardClaims.ADDRESS));
        assertEquals(capturedValue.get(StandardClaims.EMAIL), expectedJwt.get(StandardClaims.EMAIL));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_openid() {
        ReflectionTestUtils.setField(idTokenService, "legacyOpenidScope", false);

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setIss(null);
        expectedJwt.setAud("client-id");
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));

        final JWT capturedValue = jwtCaptor.getValue();
        assertEquals(capturedValue.getSub(), expectedJwt.getSub());
        assertEquals(capturedValue.getAud(), expectedJwt.getAud());
        assertNull(capturedValue.get(StandardClaims.ADDRESS));
        assertNull(capturedValue.get(StandardClaims.EMAIL));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_fullProfile() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "full_profile")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.put(StandardClaims.WEBSITE, user.getAdditionalInformation().get(StandardClaims.WEBSITE));
        expectedJwt.put(StandardClaims.ZONEINFO, user.getAdditionalInformation().get(StandardClaims.ZONEINFO));
        expectedJwt.put(StandardClaims.BIRTHDATE, user.getAdditionalInformation().get(StandardClaims.BIRTHDATE));
        expectedJwt.put(StandardClaims.GENDER, user.getAdditionalInformation().get(StandardClaims.GENDER));
        expectedJwt.put(StandardClaims.PROFILE, user.getAdditionalInformation().get(StandardClaims.PROFILE));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.PREFERRED_USERNAME, user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
        expectedJwt.put(StandardClaims.GIVEN_NAME, user.getAdditionalInformation().get(StandardClaims.GIVEN_NAME));
        expectedJwt.put(StandardClaims.MIDDLE_NAME, user.getAdditionalInformation().get(StandardClaims.MIDDLE_NAME));
        expectedJwt.put(StandardClaims.LOCALE, user.getAdditionalInformation().get(StandardClaims.LOCALE));
        expectedJwt.put(StandardClaims.PICTURE, user.getAdditionalInformation().get(StandardClaims.PICTURE));
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.UPDATED_AT, user.getAdditionalInformation().get(StandardClaims.UPDATED_AT));
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.put(StandardClaims.NICKNAME, user.getAdditionalInformation().get(StandardClaims.NICKNAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.FAMILY_NAME, user.getAdditionalInformation().get(StandardClaims.FAMILY_NAME));
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.PHONE_NUMBER, user.getAdditionalInformation().get(StandardClaims.PHONE_NUMBER));
        expectedJwt.put(StandardClaims.PHONE_NUMBER_VERIFIED, user.getAdditionalInformation().get(StandardClaims.PHONE_NUMBER_VERIFIED));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));

        final JWT capturedValue = jwtCaptor.getValue();
        assertEquals(capturedValue.getSub(), expectedJwt.getSub());
        assertEquals(capturedValue.getAud(), expectedJwt.getAud());
        assertEquals(capturedValue.get(StandardClaims.ADDRESS), expectedJwt.get(StandardClaims.ADDRESS));
        assertEquals(capturedValue.get(StandardClaims.EMAIL), expectedJwt.get(StandardClaims.EMAIL));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_email() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();


        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
        JWT jwt = jwtCaptor.getValue();
        assertNotNull(jwt);
        assertTrue(jwt.get("sub") != null && expectedJwt.getSub().equals(jwt.get("sub")));
        assertTrue(jwt.get("aud") != null && expectedJwt.getAud().equals(jwt.get("aud")));
        assertTrue(jwt.get("email") != null && expectedJwt.get(StandardClaims.EMAIL).equals(jwt.get("email")));
        assertTrue(jwt.get("address") == null);
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_email_address() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email", "address")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(jwtCaptor.capture(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager).findByAlgorithm(any());
        verify(certificateManager).get(any());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
        JWT jwt = jwtCaptor.getValue();
        assertNotNull(jwt);
        assertTrue(jwt.get("sub") != null && expectedJwt.getSub().equals(jwt.get("sub")));
        assertTrue(jwt.get("aud") != null && expectedJwt.getAud().equals(jwt.get("aud")));
        assertTrue(jwt.get("email") != null && expectedJwt.get(StandardClaims.EMAIL).equals(jwt.get("email")));
        assertTrue(jwt.get("address") != null && expectedJwt.get(StandardClaims.ADDRESS).equals(jwt.get("address")));
    }

    @Test
    public void shouldCreateIDToken_withUser_scopesRequest_and_claimsRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email", "address")));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"name\":{\"essential\":true}}}"));
        oAuth2Request.setParameters(requestParameters);

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        when(certificateManager.findByAlgorithm(any())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(any())).thenReturn(Maybe.just(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));
        ((IDTokenServiceImpl) idTokenService).setObjectMapper(objectMapper);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(any());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.certificate.CertificateProvider.class));
    }

    private User createUser() {
        User user = new User();
        user.setId("technical-id");
        Map<String, Object> additionalInformation  = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, "user");
        additionalInformation.put(StandardClaims.NAME, "gravitee user");
        additionalInformation.put(StandardClaims.FAMILY_NAME, "gravitee");
        additionalInformation.put(StandardClaims.GIVEN_NAME, "gravitee");
        additionalInformation.put(StandardClaims.MIDDLE_NAME, "gravitee");
        additionalInformation.put(StandardClaims.NICKNAME, "gravitee");
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, "gravitee");
        additionalInformation.put(StandardClaims.PROFILE, "gravitee");
        additionalInformation.put(StandardClaims.PICTURE, "gravitee");
        additionalInformation.put(StandardClaims.WEBSITE, "gravitee");
        additionalInformation.put(StandardClaims.GENDER, "gravitee");
        additionalInformation.put(StandardClaims.BIRTHDATE, "gravitee");
        additionalInformation.put(StandardClaims.ZONEINFO, "gravitee");
        additionalInformation.put(StandardClaims.LOCALE, "gravitee");
        additionalInformation.put(StandardClaims.UPDATED_AT, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL_VERIFIED, "gravitee");
        additionalInformation.put(StandardClaims.ADDRESS, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER_VERIFIED, "gravitee");
        user.setAdditionalInformation(additionalInformation);

        return user;
    }
}
