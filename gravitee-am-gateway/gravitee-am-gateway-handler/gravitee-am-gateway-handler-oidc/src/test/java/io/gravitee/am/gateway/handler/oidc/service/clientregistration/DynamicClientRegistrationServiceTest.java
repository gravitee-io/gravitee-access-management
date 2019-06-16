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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationServiceTest {

    @InjectMocks
    private DynamicClientRegistrationService dcrService = new DynamicClientRegistrationServiceImpl();

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private CertificateService certificateService;

    @Mock
    private JWKService jwkService;

    @Mock
    private JWTService jwtService;

    @Mock
    public WebClient webClient;

    @Mock
    private Domain domain;

    @Test
    public void create() {
        String domainId = "domain";
        String clientName = "name";

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setClientName(Optional.of(clientName));

        when(domain.getId()).thenReturn(domainId);
        Client result = dcrService.create(request);
        assertTrue(result.getDomain().equals(domainId));
        assertTrue(result.getClientName().equals(clientName));
    }

    @Test
    public void applyDefaultIdentiyProvider_noIdentityProvider() {
        when(identityProviderService.findByDomain(any())).thenReturn(Single.just(Collections.emptyList()));

        TestObserver testObserver = dcrService.applyDefaultIdentityProvider(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client!=null && ((Client)client).getIdentities()==null);
    }

    @Test
    public void applyDefaultIdentiyProvider() {
        IdentityProvider identityProvider = Mockito.mock(IdentityProvider.class);
        when(identityProvider.getId()).thenReturn("identity-provider-id-123");

        when(identityProviderService.findByDomain(any())).thenReturn(Single.just(Arrays.asList(identityProvider)));

        TestObserver testObserver = dcrService.applyDefaultIdentityProvider(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client!=null && ((Client)client).getIdentities().contains("identity-provider-id-123"));
    }

    @Test
    public void applyDefaultCertificateProvider_noCertificateProvider() {
        when(certificateService.findByDomain(any())).thenReturn(Single.just(Collections.emptyList()));

        TestObserver testObserver = dcrService.applyDefaultCertificateProvider(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client!=null && ((Client)client).getCertificate()==null);
    }

    @Test
    public void applyDefaultCertificateProvider_default() {
        Certificate certificate = Mockito.mock(Certificate.class);
        when(certificate.getId()).thenReturn("certificate-id-123");

        when(certificateService.findByDomain(any())).thenReturn(Single.just(Arrays.asList(certificate)));

        TestObserver testObserver = dcrService.applyDefaultCertificateProvider(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client!=null && ((Client)client).getCertificate().equals("certificate-id-123"));
    }

    @Test
    public void applyRegistrationAccessToken() {
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        when(jwtService.encode(any(),any(Client.class))).thenReturn(Single.just("token"));
        when(openIDDiscoveryService.getConfiguration(any())).thenReturn(openIDProviderMetadata);
        when(openIDProviderMetadata.getIssuer()).thenReturn("https://issuer");
        when(openIDProviderMetadata.getRegistrationEndpoint()).thenReturn("https://issuer/register");

        TestObserver testObserver = dcrService.applyRegistrationAccessToken(any(),new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client!=null && ((Client)client).getRegistrationAccessToken().equals("token"));
    }

    @Test
    public void validateClientRegistrationRequest_nullRequest() {
        TestObserver testObserver = dcrService.validateClientRegistrationRequest(null).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("One of the Client Metadata value is invalid.");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_nullRedirectUriRequest() {
        TestObserver testObserver = dcrService.validateClientRegistrationRequest(new DynamicClientRegistrationRequest()).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_emptyRedirectUriRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        //Allow null values on redirect_uris (ex client_credentials...)
        request.setRedirectUris(Optional.empty());//"redirect_uris": null

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_emptyArrayRedirectUriRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_notAllowedScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setScope(Optional.of("not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        //scope is not allowed, so expecting to erase scope (no scope)
        testObserver.assertValue(o -> !((DynamicClientRegistrationRequest)o).getScope().isPresent());
    }

    @Test
    public void validateClientRegistrationRequest_notAllowedScopes_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setScope(Optional.of("not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().size()==2);
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().containsAll(Arrays.asList("phone","email")));
    }

    @Test
    public void validateClientRegistrationRequest_filteredScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().size()==1);
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().get(0).equals("openid"));
    }

    @Test
    public void validateClientRegistrationRequest_defaultScopes_notUsed() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().size()==3);
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().containsAll(Arrays.asList("openid","not","allowed")));
    }

    @Test
    public void validateClientRegistrationRequest_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().size()==2);
        testObserver.assertValue(o -> ((DynamicClientRegistrationRequest)o).getScope().get().containsAll(Arrays.asList("phone","email")));
    }

    @Test
    public void validateClientRegistrationRequest_unknownResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setResponseTypes(Optional.of(Arrays.asList("unknownResponseType")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Invalid response type.");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_ok_emptyResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setResponseTypes(Optional.empty());

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unknownGrantTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setGrantTypes(Optional.of(Arrays.asList("unknownGrantType")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Missing or invalid grant type.");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedSubjectTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSubjectType(Optional.of("unknownSubjectType"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported subject type");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedUserinfoSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo signing algorithm");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo_encrypted_response_alg value");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_missingUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("When userinfo_encrypted_response_enc is included, userinfo_encrypted_response_alg MUST also be provided");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedUserinfoResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));
        request.setUserinfoEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo_encrypted_response_enc value");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_defaultUserinfoResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(result -> result.getUserinfoEncryptedResponseEnc()!=null);
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedIdTokenSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token signing algorithm");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token_encrypted_response_alg value");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_missingIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("When id_token_encrypted_response_enc is included, id_token_encrypted_response_alg MUST also be provided");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedIdTokenResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));
        request.setIdTokenEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token_encrypted_response_enc value");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_defaultIdTokenResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(result -> result.getIdTokenEncryptedResponseEnc()!=null);
    }

    @Test
    public void validateClientRegistrationRequest_unvalidRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setRequestUris(Optional.of(Arrays.asList("nonValidUri")));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("request_uris:"));
    }

    @Test
    public void validateClientRegistrationRequest_validRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setRequestUris(Optional.of(Arrays.asList("https://valid/request/uri")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }


    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUriBadFormat() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of("blabla"));//fail due to invalid url

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("sector_identifier_uri:"));
    }

    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUriNottHttps() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of("http://something"));//fail due to invalid url

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("Scheme must be https for sector_identifier_uri"));
    }

    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUriBadRequest() {
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of(sectorUri));//fail due to invalid url
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("Unable to parse sector_identifier_uri"));
    }

    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUri_invalidRedirectUri() {
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of(sectorUri));//fail due to invalid url
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.bodyAsString()).thenReturn("[\"https://not/same/redirect/uri\"]");

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUri_validRedirectUri() {
        final String redirectUri = "https://graviee.io/callback";
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList(redirectUri)));
        request.setSectorIdentifierUri(Optional.of(sectorUri));//fail due to invalid url
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.bodyAsString()).thenReturn("[\""+redirectUri+"\"]");

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateJWKsDuplicatedSource() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwks(Optional.of(new JWKSet()));
        request.setJwksUri(Optional.of("something"));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("The jwks_uri and jwks parameters MUST NOT be used together.");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateJWKsUriWithoutJwkSet() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.empty());

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("No JWK found behind jws uri...");
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateJWKsUriOk() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_ok() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_client_credentials_ok() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setGrantTypes(Optional.of(Arrays.asList("client_credentials")));
        request.setResponseTypes(Optional.of(Arrays.asList()));
        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validatePatchRequest_nullRequest() {
        TestObserver testObserver = dcrService.validateClientPatchRequest(null).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("One of the Client Metadata value is invalid.");
        testObserver.assertNotComplete();
    }

    @Test
    public void validatePatchRequest_emptyRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientPatchRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validatePatchRequest_emptyRedirectUri() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        TestObserver testObserver = dcrService.validateClientPatchRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validatePatchRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientPatchRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }
}
