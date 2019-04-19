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

import io.gravitee.am.gateway.handler.common.jwk.JwkService;
import io.gravitee.am.gateway.handler.common.jwt.JwtService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.JWKSet;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
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
    private JwkService jwkService;

    @Mock
    private JwtService jwtService;

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
        request.setRedirectUris(Optional.empty());

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_emptyArrayRedirectUriRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();
    }


    @Test
    public void validateClientRegistrationRequest_unknownResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setResponseTypes(Optional.of(Arrays.asList("unknownResponseType")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unknownGrantTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setGrantTypes(Optional.of(Arrays.asList("unknownGrantType")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedSubjectTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSubjectType(Optional.of("unknownSubjectType"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedUserinfoSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setUserinfoSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unsupportedIdTokenSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setIdTokenSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_unvalidRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setRequestUris(Optional.of(Arrays.asList("nonValidUri")));

        TestObserver testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
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
    }

    @Test
    public void validateClientRegistrationRequest_sectorIdentifierUriNottHttps() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of("http://something"));//fail due to invalid url

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
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
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateJWKsUriWithoutJwkSet() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(any())).thenReturn(Maybe.empty());

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateJWKsUriOk() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(any())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void validateClientRegistrationRequest_validateScope_noOpenidScopeRequested() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setScope(Optional.of("scope1 test"));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientRegistrationRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(req -> req!=null && req.getScope().get().contains("openid"));
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
    public void validatePatchRequest_nullRequest() {
        TestObserver testObserver = dcrService.validateClientPatchRequest(null).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validatePatchRequest() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(any())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<DynamicClientRegistrationRequest> testObserver = dcrService.validateClientPatchRequest(request).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }
}
