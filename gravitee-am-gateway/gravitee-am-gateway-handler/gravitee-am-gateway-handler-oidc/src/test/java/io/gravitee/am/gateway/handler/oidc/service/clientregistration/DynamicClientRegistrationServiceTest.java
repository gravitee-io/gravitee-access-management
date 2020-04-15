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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.reactivex.Completable;
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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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
    private OpenIDProviderMetadata openIDProviderMetadata;

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
    public ClientService clientService;

    @Mock
    private Domain domain;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    private static final String DOMAIN_ID = "domain";
    private static final String BASE_PATH = "";
    private static final String ID_SOURCE = "123";
    private static final String ID_TARGET = "abc";

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Collections.emptyList()));
        when(openIDProviderMetadata.getRegistrationEndpoint()).thenReturn("https://issuer/register");
        when(openIDDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(openIDProviderMetadata);
        when(openIDProviderMetadata.getIssuer()).thenReturn("https://issuer");
        when(jwtService.encode(any(JWT.class),any(Client.class))).thenReturn(Single.just("jwt"));

        when(clientService.create(any())).thenAnswer(i -> {
            Client res = i.getArgument(0);
            res.setId(ID_TARGET);
            return Single.just(res);
        });
        when(clientService.update(any())).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(clientService.delete(any())).thenReturn(Completable.complete());
        when(clientService.renewClientSecret(any(), any())).thenAnswer(i -> {
            Client toRenew = new Client();
            toRenew.setClientSecret("secretRenewed");
            return Single.just(toRenew);
        });
    }

    @Test
    public void create_nullRequest() {
        TestObserver<Client> testObserver = dcrService.create(null, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("One of the Client Metadata value is invalid.");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingRedirectUri() {
        TestObserver<Client> testObserver = dcrService.create(new DynamicClientRegistrationRequest(), BASE_PATH).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertErrorMessage("Missing or invalid redirect_uris.");//redirect_uri metadata can be null but is mandatory
        testObserver.assertNotComplete();
    }

    @Test
    public void create_emptyRedirectUriArray() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(client -> client.getRedirectUris().isEmpty());
    }

    @Test
    public void create_defaultCase() {
        String clientName = "name";

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setClientName(Optional.of(clientName));
        request.setRedirectUris(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request,BASE_PATH).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getClientName().equals(clientName) &&
                client.getIdentities() == null &&
                client.getCertificate() == null
        );
        verify(clientService, times(1)).create(any());
    }

    @Test
    public void create_applyDefaultIdentiyProvider() {
        IdentityProvider identityProvider = Mockito.mock(IdentityProvider.class);
        when(identityProvider.getId()).thenReturn("identity-provider-id-123");
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Single.just(Arrays.asList(identityProvider)));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getIdentities().contains("identity-provider-id-123"));
    }

    @Test
    public void create_applyDefaultCertificateProvider() {
        Certificate certificate = Mockito.mock(Certificate.class);
        when(certificate.getId()).thenReturn("certificate-id-123");
        when(certificateService.findByDomain(any())).thenReturn(Single.just(Arrays.asList(certificate)));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getCertificate().equals("certificate-id-123"));
    }

    @Test
    public void create_notAllowedScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        //scope is not allowed, so expecting to erase scope (no scope)
        testObserver.assertValue(client -> this.defaultAssertion(client) && client.getScopes()==null);
    }

    @Test
    public void create_notAllowedScopes_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopes().size() == 2 &&
                client.getScopes().containsAll(Arrays.asList("phone","email"))
        );
    }

    @Test
    public void create_filteredScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid","profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopes().size() == 1 &&
                client.getScopes().containsAll(Arrays.asList("openid"))
        );
    }

    @Test
    public void create_defaultScopes_notUsed() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopes().size() == 3 &&
                client.getScopes().containsAll(Arrays.asList("openid","not","allowed"))
        );
    }

    @Test
    public void create_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone","email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopes().size() == 2 &&
                client.getScopes().containsAll(Arrays.asList("phone","email"))
        );
    }

    @Test
    public void create_emptyResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setResponseTypes(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getResponseTypes()==null);
    }

    @Test
    public void create_unknownResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setResponseTypes(Optional.of(Arrays.asList("unknownResponseType")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Invalid response type.");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unknownGrantTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setGrantTypes(Optional.of(Arrays.asList("unknownGrantType")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Missing or invalid grant type.");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedSubjectTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSubjectType(Optional.of("unknownSubjectType"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported subject type");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedUserinfoSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo signing algorithm");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo_encrypted_response_alg value");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("When userinfo_encrypted_response_enc is included, userinfo_encrypted_response_alg MUST also be provided");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedUserinfoResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));
        request.setUserinfoEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported userinfo_encrypted_response_enc value");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_defaultUserinfoResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getUserinfoEncryptedResponseEnc()!=null);
    }

    @Test
    public void create_unsupportedIdTokenSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token signing algorithm");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token_encrypted_response_alg value");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("When id_token_encrypted_response_enc is included, id_token_encrypted_response_alg MUST also be provided");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedIdTokenResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));
        request.setIdTokenEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Unsupported id_token_encrypted_response_enc value");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_defaultIdTokenResponseEncPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseAlg(Optional.of("RSA-OAEP-256"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getIdTokenEncryptedResponseEnc()!=null);
    }

    @Test
    public void create_invalidRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestUris(Optional.of(Arrays.asList("nonValidUri")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("request_uris:"));
    }

    @Test
    public void create_invalidTlsParameters_noTlsField() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.TLS_CLIENT_AUTH));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().equals("Missing TLS parameter for tls_client_auth."));
    }

    @Test
    public void create_invalidTlsParameters_multipleTlsField() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.TLS_CLIENT_AUTH));
        request.setTlsClientAuthSubjectDn(Optional.of("subject-dn"));
        request.setTlsClientAuthSanDns(Optional.of("san-dns"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().equals("The tls_client_auth must use exactly one of the TLS parameters."));
    }

    @Test
    public void create_invalidSelfSignedClient_noJWKS() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().equals("The self_signed_tls_client_auth requires at least a jwks or a valid jwks_uri."));
    }

    @Test
    public void create_validSelfSignedClient() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setJwks(Optional.of(new JWKSet()));
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void create_validRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestUris(Optional.of(Arrays.asList("https://valid/request/uri")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getRequestUris().contains("https://valid/request/uri"));
    }


    @Test
    public void create_sectorIdentifierUriBadFormat() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSectorIdentifierUri(Optional.of("blabla"));//fail due to invalid url

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("sector_identifier_uri:"));
    }

    @Test
    public void create_sectorIdentifierUriNottHttps() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSectorIdentifierUri(Optional.of("http://something"));//fail due to invalid url

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("Scheme must be https for sector_identifier_uri"));
    }

    @Test
    public void create_sectorIdentifierUriBadRequest() {
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSectorIdentifierUri(Optional.of(sectorUri));//fail due to invalid url
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        assertTrue("Should have only one exception", testObserver.errorCount()==1);
        assertTrue("Unexpected start of error message", testObserver.errors().get(0).getMessage().startsWith("Unable to parse sector_identifier_uri"));
    }

    @Test
    public void create_sectorIdentifierUri_invalidRedirectUri() {
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setSectorIdentifierUri(Optional.of(sectorUri));//fail due to invalid url
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.bodyAsString()).thenReturn("[\"https://not/same/redirect/uri\"]");

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void create_sectorIdentifierUri_validRedirectUri() {
        final String redirectUri = "https://graviee.io/callback";
        final String sectorUri = "https://sector/uri";
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList(redirectUri)));
        request.setSectorIdentifierUri(Optional.of(sectorUri));
        HttpRequest<Buffer> httpRequest = Mockito.mock(HttpRequest.class);
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);

        when(webClient.getAbs(sectorUri)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.bodyAsString()).thenReturn("[\""+redirectUri+"\"]");

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void create_validateJWKsDuplicatedSource() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setJwks(Optional.of(new JWKSet()));
        request.setJwksUri(Optional.of("something"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("The jwks_uri and jwks parameters MUST NOT be used together.");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_validateJWKsUriWithoutJwkSet() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("No JWK found behind jws uri...");
        testObserver.assertNotComplete();
    }

    @Test
    public void create_validateJWKsUriOk() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) && client.getJwksUri().equals("something"));
    }

    @Test
    public void create_client_credentials() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setGrantTypes(Optional.of(Arrays.asList("client_credentials")));
        request.setResponseTypes(Optional.of(Arrays.asList()));
        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getRedirectUris()==null &&
                client.getAuthorizedGrantTypes().size()==1 &&
                client.getAuthorizedGrantTypes().contains("client_credentials") &&
                client.getResponseTypes().isEmpty()
        );
    }

    @Test
    public void create_implicit() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setGrantTypes(Optional.of(Arrays.asList("implicit")));
        request.setResponseTypes(Optional.of(Arrays.asList("token")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getRedirectUris().contains("https://graviee.io/callback") &&
                client.getAuthorizedGrantTypes().size() == 1 &&
                client.getAuthorizedGrantTypes().contains("implicit") &&
                client.getResponseTypes().size() == 1 &&
                client.getResponseTypes().contains("token")
        );
    }

    private boolean defaultAssertion(Client client) {
        assertNotNull("Client is null",client);
        assertNotNull("Client id is null", client.getClientId());

        assertNull("expecting no redirect_uris", client.getRedirectUris());

        assertEquals("Domain is wrong",DOMAIN_ID,client.getDomain());
        assertEquals("registration uri is wrong", "https://issuer/register"+"/"+client.getClientId(), client.getRegistrationClientUri());
        assertEquals("registration token is wrong", "jwt", client.getRegistrationAccessToken());
        assertEquals("should be default value \"web\"", "web", client.getApplicationType());

        assertTrue("should be default value \"code\"", client.getResponseTypes().size()==1 && client.getResponseTypes().contains("code"));
        return true;
    }

    @Test
    public void patch_noRedirectUriMetadata() {
        TestObserver<Client> testObserver = dcrService.patch(new Client(), new DynamicClientRegistrationRequest(), BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void patch() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<Client> testObserver = dcrService.patch(new Client(), request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getJwksUri().equals("something") && client.getRedirectUris().size()==1);
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void update_missingRedirectUri() {
        TestObserver<Client> testObserver = dcrService.update(new Client(), new DynamicClientRegistrationRequest(), BASE_PATH).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertErrorMessage("Missing or invalid redirect_uris.");//redirect_uri metadata can be null but is mandatory
        testObserver.assertNotComplete();
    }

    @Test
    public void update() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setApplicationType(Optional.of("something"));

        TestObserver<Client> testObserver = dcrService.update(new Client(), request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getApplicationType().equals("something") && client.getRedirectUris().isEmpty());
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void delete() {
        TestObserver<Client> testObserver = dcrService.delete(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(clientService, times(1)).delete(any());
    }

    @Test
    public void renewSecret() {
        Client toRenew = new Client();
        toRenew.setId("id");
        toRenew.setDomain("domain_id");
        toRenew.setClientId("client_id");
        toRenew.setClientSecret("oldSecret");

        TestObserver<Client> testObserver = dcrService.renewSecret(toRenew, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getClientSecret().equals("secretRenewed"));
        verify(clientService, times(1)).renewClientSecret(anyString(), anyString());
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void createFromTemplate_templateNotFound() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setSoftwareId(Optional.of("123"));

        when(domain.isDynamicClientRegistrationTemplateEnabled()).thenReturn(true);
        when(clientService.findById(any())).thenReturn(Maybe.empty());

        TestObserver<Client> testObserver = dcrService.create(request,BASE_PATH).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("No template found for software_id 123");
        verify(clientService, times(0)).create(any());
    }

    @Test
    public void createFromTemplate_isNotTemplate() {
        Client template = new Client();
        template.setId("123");
        template.setClientName("shouldBeRemoved");
        template.setClientId("shouldBeReplaced");
        template.setClientSecret("shouldBeRemoved");
        template.setRedirectUris(Arrays.asList("shouldBeRemoved"));
        template.setSectorIdentifierUri("shouldBeRemoved");
        template.setJwks(new JWKSet());

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setSoftwareId(Optional.of("123"));
        request.setApplicationType(Optional.of("app"));

        when(domain.isDynamicClientRegistrationTemplateEnabled()).thenReturn(true);
        when(clientService.findById("123")).thenReturn(Maybe.just(template));

        TestObserver<Client> testObserver = dcrService.create(request,BASE_PATH).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertErrorMessage("Client behind software_id is not a template");
        verify(clientService, times(0)).create(any());
    }

    @Test
    public void createFromTemplate() {
        Client template = new Client();
        template.setId(ID_SOURCE);
        template.setClientName("shouldBeRemoved");
        template.setClientId("shouldBeReplaced");
        template.setClientSecret("shouldBeRemoved");
        template.setRedirectUris(Arrays.asList("shouldBeRemoved"));
        template.setSectorIdentifierUri("shouldBeRemoved");
        template.setJwks(new JWKSet());
        template.setTemplate(true);

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setSoftwareId(Optional.of(ID_SOURCE));
        request.setApplicationType(Optional.of("app"));

        when(formService.copyFromClient(DOMAIN_ID, ID_SOURCE, ID_TARGET)).thenReturn(Single.just(Collections.emptyList()));
        when(emailTemplateService.copyFromClient(DOMAIN_ID, ID_SOURCE, ID_TARGET)).thenReturn(Single.just(Collections.emptyList()));
        when(domain.isDynamicClientRegistrationTemplateEnabled()).thenReturn(true);
        when(clientService.findById("123")).thenReturn(Maybe.just(template));

        TestObserver<Client> testObserver = dcrService.create(request,BASE_PATH).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(client ->
                client.getId().equals("abc") &&
                client.getApplicationType().equals("app") &&
                client.getClientId() != null &&
                !client.getClientId().equals("shouldBeReplaced") &&
                client.getRedirectUris() == null &&
                client.getClientName() == null &&
                client.getClientSecret() == null &&
                client.getJwks() == null &&
                client.getSectorIdentifierUri() == null
        );
        verify(clientService, times(1)).create(any());
    }
}
