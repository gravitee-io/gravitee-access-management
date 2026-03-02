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

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.CIBADeliveryMode;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.ClientServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DEFAULT_ACCESS_TOKEN_VALIDITY;
import static io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl.OPENID_DCR_ACCESS_TOKEN_VALIDITY;
import static io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl.OPENID_DCR_ID_TOKEN_VALIDITY;
import static io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl.DynamicClientRegistrationServiceImpl.OPENID_DCR_REFRESH_TOKEN_VALIDITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientRegistrationServiceTest {

    public static final String DUMMY_JWKS_URI = "https://somewhere/jwks";
    public static final String DUMMY_REDIRECTURI = "https://redirecturi";
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
    private JWSService jwsService;

    @Mock
    private JWTService jwtService;

    @Mock
    public WebClient webClient;

    @Mock
    public ClientService clientService;

    @Mock
    private Domain domain;

    @Mock
    private FlowService flowService;

    @Mock
    private FormService formService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private Environment environment;

    @Mock
    private SecretService secretService;

    @Mock
    private ClientSecretService clientSecretService;

    private static final String DOMAIN_ID = "domain";
    private static final Domain DOMAIN = new Domain(DOMAIN_ID);
    private static final String BASE_PATH = "";
    private static final String ID_SOURCE = "123";
    private static final String ID_TARGET = "abc";

    @Before
    public void setUp() {
        reset(domain, environment);

        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(certificateService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.empty());
        when(openIDProviderMetadata.getRegistrationEndpoint()).thenReturn("https://issuer/register");
        when(openIDDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(openIDProviderMetadata);
        when(openIDProviderMetadata.getIssuer()).thenReturn("https://issuer");
        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("jwt"));

        when(clientService.create(any(), any())).thenAnswer(i -> {
            Client res = i.getArgument(1);
            res.setId(ID_TARGET);
            return Single.just(res);
        });
        when(clientService.update(any())).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(clientService.delete(any(), any())).thenReturn(Completable.complete());

        when(domain.useFapiBrazilProfile()).thenReturn(false);
        when(environment.getProperty(OPENID_DCR_ACCESS_TOKEN_VALIDITY, Integer.class, Client.DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS)).thenReturn(Client.DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS);
        when(environment.getProperty(OPENID_DCR_ACCESS_TOKEN_VALIDITY, Integer.class, FAPI_OPENBANKING_BRAZIL_DEFAULT_ACCESS_TOKEN_VALIDITY)).thenReturn(FAPI_OPENBANKING_BRAZIL_DEFAULT_ACCESS_TOKEN_VALIDITY);
        when(environment.getProperty(OPENID_DCR_REFRESH_TOKEN_VALIDITY, Integer.class, Client.DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS)).thenReturn(1000);
        when(environment.getProperty(OPENID_DCR_ID_TOKEN_VALIDITY, Integer.class, Client.DEFAULT_ID_TOKEN_VALIDITY_SECONDS)).thenReturn(1100);
    }

    @Test
    public void create_nullRequest() {
        TestObserver<Client> testObserver = dcrService.create(null, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "One of the Client Metadata value is invalid.".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingRedirectUri() {
        TestObserver<Client> testObserver = dcrService.create(new DynamicClientRegistrationRequest(), BASE_PATH).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertError(throwable -> "Missing or invalid redirect_uris.".equals(
                throwable.getMessage()));//redirect_uri metadata can be null but is mandatory
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


        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getClientName().equals(clientName) &&
                client.getIdentityProviders() == null &&
                client.getCertificate() == null
        );
        verify(clientService, times(1)).create(any(), any());
    }

    @Test
    public void create_applyDefaultIdentityProvider() {
        IdentityProvider identityProvider = Mockito.mock(IdentityProvider.class);
        when(identityProvider.getId()).thenReturn("identity-provider-id-123");
        when(identityProviderService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(identityProvider));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> defaultAssertion(client) &&
                client.getIdentityProviders().stream().anyMatch(appIdp -> appIdp.getIdentity().equals("identity-provider-id-123"))
        );
    }

    @Test
    public void create_applyDefaultCertificateProvider() {
        Certificate certificate = Mockito.mock(Certificate.class);
        when(certificate.getId()).thenReturn("certificate-id-123");
        when(certificateService.findByDomain(any())).thenReturn(Flowable.just(certificate));

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
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid", "profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        //scope is not allowed, so expecting to erase scope (no scope)
        testObserver.assertValue(client -> this.defaultAssertion(client) && client.getScopeSettings() == null);
    }

    @Test
    public void create_notAllowedScopes_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid", "profile"));
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone", "email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopeSettings().size() == 2 &&
                client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).collect(Collectors.toList())
                        .containsAll(Arrays.asList("phone", "email"))
        );
    }

    @Test
    public void create_filteredScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setAllowedScopesEnabled(true);
        oidc.getClientRegistrationSettings().setAllowedScopes(Arrays.asList("openid", "profile"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopeSettings().size() == 1 &&
                !client.getScopeSettings()
                        .stream().filter(setting -> setting.getScope().equalsIgnoreCase("openid")).findFirst().isEmpty()
        );
    }

    @Test
    public void create_defaultScopes_notUsed() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setScope(Optional.of("openid not allowed"));

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone", "email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopeSettings().size() == 3 &&
                client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).collect(Collectors.toList())
                        .containsAll(Arrays.asList("openid", "not", "allowed"))
        );
    }

    @Test
    public void create_defaultScopes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        OIDCSettings oidc = OIDCSettings.defaultSettings();
        oidc.getClientRegistrationSettings().setDefaultScopes(Arrays.asList("phone", "email"));
        when(domain.getOidc()).thenReturn(oidc);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> this.defaultAssertion(client) &&
                client.getScopeSettings().size() == 2 &&
                client.getScopeSettings().stream().map(ApplicationScopeSettings::getScope).collect(Collectors.toList())
                        .containsAll(Arrays.asList("phone", "email"))
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
        testObserver.assertValue(client -> client.getResponseTypes() == null);
    }

    @Test
    public void create_unknownResponseTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setResponseTypes(Optional.of(Arrays.asList("unknownResponseType")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Invalid response type.".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unknownGrantTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setGrantTypes(Optional.of(Arrays.asList("unknownGrantType")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Missing or invalid grant type.".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedSubjectTypePayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSubjectType(Optional.of("unknownSubjectType"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported subject type".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedUserinfoSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported userinfo signing algorithm".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported userinfo_encrypted_response_alg value".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingUserinfoResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setUserinfoEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "When userinfo_encrypted_response_enc is included, userinfo_encrypted_response_alg MUST also be provided".equals(
                throwable.getMessage()));
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
        testObserver.assertError(throwable -> "Unsupported userinfo_encrypted_response_enc value".equals(
                throwable.getMessage()));
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
        testObserver.assertValue(client -> defaultAssertion(client) && client.getUserinfoEncryptedResponseEnc() != null);
    }

    @Test
    public void create_unsupportedIdTokenSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported id_token signing algorithm".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseAlg(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported id_token_encrypted_response_alg value".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingIdTokenResponseAlgPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setIdTokenEncryptedResponseEnc(Optional.of("unknownEncryptionAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "When id_token_encrypted_response_enc is included, id_token_encrypted_response_alg MUST also be provided".equals(
                throwable.getMessage()));
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
        testObserver.assertError(throwable -> "Unsupported id_token_encrypted_response_enc value".equals(
                throwable.getMessage()));
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
        testObserver.assertValue(client -> defaultAssertion(client) && client.getIdTokenEncryptedResponseEnc() != null);
    }

    @Test
    public void create_invalidRequestUris() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestUris(Optional.of(Arrays.asList("nonValidUri")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("request_uris:"));
    }

    @Test
    public void create_invalidTlsParameters_noTlsField() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.TLS_CLIENT_AUTH));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("Missing TLS parameter for tls_client_auth."));
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
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("The tls_client_auth must use exactly one of the TLS parameters."));
    }

    @Test
    public void create_invalidSelfSignedClient_noJWKS() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> "The self_signed_tls_client_auth requires at least a jwks or a valid jwks_uri.".equals(throwable.getMessage()));
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
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("sector_identifier_uri:"));
    }

    @Test
    public void create_sectorIdentifierUriNottHttps() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setSectorIdentifierUri(Optional.of("http://something"));//fail due to invalid url

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("Scheme must be https for sector_identifier_uri"));
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
        testObserver.assertNoValues();
        testObserver.assertError(throwable -> throwable.getMessage().startsWith("Unable to parse sector_identifier_uri"));
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
        when(httpResponse.bodyAsString()).thenReturn("[\"" + redirectUri + "\"]");

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
        testObserver.assertError(throwable -> "The jwks_uri and jwks parameters MUST NOT be used together.".equals(
                throwable.getMessage()));
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
        testObserver.assertError(throwable -> "No JWK found behind jws uri...".equals(
                throwable.getMessage()));
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
        testObserver.assertValue(client -> client.getRedirectUris() == null &&
                client.getAuthorizedGrantTypes().size() == 1 &&
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
        assertNotNull("Client is null", client);
        assertNotNull("Client id is null", client.getClientId());

        assertNull("expecting no redirect_uris", client.getRedirectUris());

        assertEquals("Domain is wrong", DOMAIN_ID, client.getDomain());
        assertEquals("registration uri is wrong", "https://issuer/register" + "/" + client.getClientId(), client.getRegistrationClientUri());
        assertEquals("registration token is wrong", "jwt", client.getRegistrationAccessToken());
        assertEquals("should be default value \"web\"", "web", client.getApplicationType());

        assertTrue("should be default value \"code\"", client.getResponseTypes().size() == 1 && client.getResponseTypes().contains("code"));
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
        testObserver.assertValue(client -> client.getJwksUri().equals("something") && client.getRedirectUris().size() == 1);
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void patch_non_hashed_secret_should_be_provided_in_response() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        final var app = new Client();

        final var bcryptSecretSettings = new ApplicationSecretSettings();
        bcryptSecretSettings.setAlgorithm(SecretHashAlgorithm.BCRYPT.name());
        bcryptSecretSettings.setId(UUID.randomUUID().toString());
        final var noneSecretSettings = new ApplicationSecretSettings();
        noneSecretSettings.setAlgorithm(SecretHashAlgorithm.NONE.name());
        noneSecretSettings.setId(UUID.randomUUID().toString());
        app.setSecretSettings(List.of(noneSecretSettings, bcryptSecretSettings));

        final var noneClientSecret = new ClientSecret();
        noneClientSecret.setSettingsId(noneSecretSettings.getId());
        noneClientSecret.setSecret(UUID.randomUUID().toString());
        final var bcryptClientSecret = new ClientSecret();
        bcryptClientSecret.setSettingsId(bcryptSecretSettings.getId());
        bcryptClientSecret.setSecret(UUID.randomUUID().toString());

        app.setClientSecrets(List.of(bcryptClientSecret, noneClientSecret));

        TestObserver<Client> testObserver = dcrService.patch(app, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> noneClientSecret.getSecret().equals(client.getClientSecret()));
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void patch_secret_should_not_be_provided_in_response_if_hashed() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://graviee.io/callback")));
        request.setJwksUri(Optional.of("something"));

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        final var app = new Client();

        final var bcryptSecretSettings = new ApplicationSecretSettings();
        bcryptSecretSettings.setAlgorithm(SecretHashAlgorithm.BCRYPT.name());
        bcryptSecretSettings.setId(UUID.randomUUID().toString());
        final var noneSecretSettings = new ApplicationSecretSettings();
        noneSecretSettings.setAlgorithm(SecretHashAlgorithm.NONE.name());
        noneSecretSettings.setId(UUID.randomUUID().toString());
        app.setSecretSettings(List.of(noneSecretSettings, bcryptSecretSettings));

        final var bcryptClientSecret = new ClientSecret();
        bcryptClientSecret.setSettingsId(bcryptSecretSettings.getId());
        bcryptClientSecret.setSecret(UUID.randomUUID().toString());

        app.setClientSecrets(List.of(bcryptClientSecret));

        TestObserver<Client> testObserver = dcrService.patch(app, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getClientSecret() == null);
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void update_missingRedirectUri() {
        TestObserver<Client> testObserver = dcrService.update(new Client(), new DynamicClientRegistrationRequest(), BASE_PATH).test();
        testObserver.assertError(InvalidRedirectUriException.class);
        testObserver.assertError(throwable -> "Missing or invalid redirect_uris.".equals(
                throwable.getMessage()));//redirect_uri metadata can be null but is mandatory
        testObserver.assertNotComplete();
    }

    @Test
    public void update() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList()));
        request.setApplicationType(Optional.of("web"));

        TestObserver<Client> testObserver = dcrService.update(new Client(), request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getApplicationType().equals("web") && client.getRedirectUris().isEmpty());
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void delete() {
        TestObserver<Client> testObserver = dcrService.delete(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(clientService, times(1)).delete(any(), any());
    }

    @Test
    public void renewSecret() {
        Client toRenew = new Client();
        toRenew.setId("id");
        toRenew.setDomain("domain_id");
        toRenew.setClientId("client_id");
        toRenew.setClientSecret("oldSecret");

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("oldSecret");
        clientSecret.setId("secret-id");
        when(clientSecretService.determineClientSecret(toRenew)).thenReturn(Optional.of(clientSecret));
        when(clientSecretService.getSecretId(any(), any(), any())).thenReturn("secret-id");

        List<ClientSecret> clientSecrets = new ArrayList<>();
        clientSecrets.add(clientSecret);
        toRenew.setClientSecrets(clientSecrets);

        when(clientService.renewClientSecret(any(), any(), any())).thenAnswer(i -> {
            Client c = i.getArgument(1);
            c.getClientSecrets().getFirst().setSecret("renewedSecret");
            return Single.just(c);
        });

        TestObserver<Client> testObserver = dcrService.renewSecret(toRenew, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getClientSecrets().stream().map(ClientSecret::getSecret).anyMatch(s -> s.equals("renewedSecret")));
        verify(clientService, times(1)).renewClientSecret(any(), any(), anyString());
        verify(clientService, times(1)).update(any());
    }

    @Test
    public void createFromTemplate_templateNotFound() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setSoftwareId(Optional.of("123"));

        when(domain.isDynamicClientRegistrationTemplateEnabled()).thenReturn(true);
        when(clientService.findById(any())).thenReturn(Maybe.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "No template found for software_id 123".equals(
                throwable.getMessage()));
        verify(clientService, times(0)).create(any(), any());
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

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Client behind software_id is not a template".equals(
                throwable.getMessage()));
        verify(clientService, times(0)).create(any(), any());
    }

    @Test
    public void createFromTemplate() {
        Client template = new Client();
        template.setId(ID_SOURCE);
        template.setClientName("shouldBeRemoved");
        template.setClientId("shouldBeReplaced");
        template.setClientSecret("shouldBeRemoved");
        template.setSecretSettings(List.of(new ApplicationSecretSettings()));
        template.setClientSecrets(List.of(new ClientSecret()));
        template.setRedirectUris(Arrays.asList("shouldBeRemoved"));
        template.setSectorIdentifierUri("shouldBeRemoved");
        template.setJwks(new JWKSet());
        template.setTemplate(true);
        template.setAccessTokenValiditySeconds(3600);
        template.setRefreshTokenValiditySeconds(1800);
        template.setIdTokenValiditySeconds(900);
        template.setMfaSettings(new MFASettings());
        template.setRiskAssessment(new RiskAssessmentSettings());
        template.setPasswordSettings(new PasswordSettings());
        template.setCookieSettings(new CookieSettings());
        template.setLoginSettings(new LoginSettings());

        String factorId = UUID.randomUUID().toString();
        template.setFactors(Set.of(factorId));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setSoftwareId(Optional.of(ID_SOURCE));
        request.setApplicationType(Optional.of("app"));

        when(flowService.copyFromClient(DOMAIN_ID, ID_SOURCE, ID_TARGET)).thenReturn(Single.just(Collections.emptyList()));
        when(formService.copyFromClient(DOMAIN_ID, ID_SOURCE, ID_TARGET)).thenReturn(Single.just(Collections.emptyList()));
        when(emailTemplateService.copyFromClient(any(Domain.class), eq(ID_SOURCE), eq(ID_TARGET))).thenReturn(Flowable.empty());
        when(domain.isDynamicClientRegistrationTemplateEnabled()).thenReturn(true);
        when(clientService.findById("123")).thenReturn(Maybe.just(template));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(client ->
                client.getId().equals("abc") &&
                        client.getApplicationType().equals("app") &&
                        client.getClientId() != null &&
                        !client.getClientId().equals("shouldBeReplaced") &&
                        client.getRedirectUris() == null &&
                        client.getClientName().equals(ClientServiceImpl.DEFAULT_CLIENT_NAME) &&
                        client.getClientSecret() == null &&
                        client.getJwks() == null &&
                        client.getSectorIdentifierUri() == null &&
                        !client.getFactors().isEmpty() &&
                        client.getFactors().iterator().next().equals(factorId) &&
                        client.getLoginSettings() != null &&
                        client.getMfaSettings() != null &&
                        client.getPasswordSettings() != null &&
                        client.getCookieSettings() != null &&
                        client.getRiskAssessment() != null &&
                        client.getAccessTokenValiditySeconds() == 3600 &&
                        client.getRefreshTokenValiditySeconds() == 1800 &&
                        client.getIdTokenValiditySeconds() == 900
        );
        verify(clientService, times(1)).create(any(), argThat(duplicateClient -> duplicateClient.getClientSecrets().isEmpty() && duplicateClient.getSecretSettings().isEmpty()));
    }

    @Test
    public void create_unsupportedAuthorizationSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setAuthorizationSignedResponseAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported authorization signing algorithm".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_undefinedAuthorizationSigningAlgorithmPayload() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> client.getAuthorizationSignedResponseAlg().equals(JWSAlgorithm.RS256.getName()));
        verify(clientService, times(1)).create(any(), any());
    }

    @Test
    public void create_unsupportedRequestObjectSigningAlg() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectSigningAlg(Optional.of("unknownSigningAlg"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported request object signing algorithm".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void fapi_create_unsupportedRequestObjectSigningAlg() {
        when(domain.usePlainFapiProfile()).thenReturn(true);
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectSigningAlg(Optional.of(JWSAlgorithm.RS256.getName()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "request_object_signing_alg shall be PS256".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_missingRequestObjectEncryptionAlgorithm() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectEncryptionAlg(null);
        request.setRequestObjectEncryptionEnc(Optional.of(JWAlgorithmUtils.getDefaultRequestObjectEnc()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "When request_object_encryption_enc is included, request_object_encryption_alg MUST also be provided".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportedRequestObjectEncryptionAlgorithm() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectEncryptionAlg(Optional.of("unknownKeyAlg"));
        request.setRequestObjectEncryptionEnc(Optional.of(JWAlgorithmUtils.getDefaultRequestObjectEnc()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported request_object_encryption_alg value".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_supportRequestObjectEncryptionAlgorithm() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(JWAlgorithmUtils.getDefaultRequestObjectEnc()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void create_unsupportedRequestObjectContentEncryptionAlgorithm() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of("unsupported"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Unsupported request_object_encryption_enc value".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_unsupportRequestObjectEncryptionAlgorithm_FapiBrazil() {
        when(domain.useFapiBrazilProfile()).thenReturn(true);

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(JWAlgorithmUtils.getDefaultRequestObjectEnc()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Request object must be encrypted using RSA-OAEP with A256GCM".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_noOpenBanking_JWKS_URI_FapiBrazil() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setSoftwareStatement(Optional.of("jws"));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(null);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "No jwks_uri for OpenBanking Directory, unable to validate software_statement".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_invalidSignatureAlg() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.RS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "software_statement isn't signed or doesn't use PS256".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_invalidSignature() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now().minus(6, ChronoUnit.MINUTES))));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(false);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "Invalid signature for software_statement".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_iatToOld() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now().minus(6, ChronoUnit.MINUTES))));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "software_statement older than 5 minutes".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_jwks_forbidden() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        final JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(new io.gravitee.am.model.jose.RSAKey()));
        request.setJwks(Optional.of(jwkSet));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "jwks is forbidden, prefer jwks_uri".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_missing_jwks_uri() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "jwks_uri is required".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_invalid_jwks_uri() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of("https://invalid"));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "jwks_uri doesn't match the software_jwks_uri".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_missing_redirect_uris() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));

        request.setRedirectUris(Optional.empty());

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "redirect_uris are missing".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_SoftwareStatement_invalid_redirect_uris() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));

        request.setRedirectUris(Optional.of(Arrays.asList("https://invalid")));

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "redirect_uris contains unknown uri from software_statement".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_tlsClientAuth_missingDN() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));
        request.setRedirectUris(Optional.of(Arrays.asList(DUMMY_REDIRECTURI)));

        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.TLS_CLIENT_AUTH));
        request.setTlsClientAuthSanEmail(Optional.of("email@domain.net"));
        request.setTlsClientAuthSubjectDn(Optional.empty());
        request.setTlsClientAuthSanDns(Optional.empty());
        request.setTlsClientAuthSanIp(Optional.empty());
        request.setTlsClientAuthSanUri(Optional.empty());

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(throwable -> "tls_client_auth_subject_dn is required with tls_client_auth as client authentication method".equals(
                throwable.getMessage()));
        testObserver.assertNotComplete();
    }

    @Test
    public void create_FapiBrazil_success() throws Exception {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));
        request.setRedirectUris(Optional.of(Arrays.asList(DUMMY_REDIRECTURI)));

        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.TLS_CLIENT_AUTH));
        request.setTlsClientAuthSubjectDn(Optional.of("Subject DN"));
        request.setTlsClientAuthSanEmail(Optional.empty());
        // request.setTlsClientAuthSanDns(Optional.empty()); // DO NOT provide, empty should be present at the end
        // request.setTlsClientAuthSanIp(Optional.empty()); // DO NOT provide it, empty should be present at the end
        request.setTlsClientAuthSanUri(Optional.empty());

        assertNull(request.getTlsClientAuthSanDns());
        assertNull(request.getTlsClientAuthSanIp());

        final RSAKey rsaKey = generateRSAKey();
        request.setSoftwareStatement(Optional.of(generateSoftwareStatement(rsaKey, JWSAlgorithm.PS256, Instant.now())));

        when(domain.useFapiBrazilProfile()).thenReturn(true);
        when(environment.getProperty(DynamicClientRegistrationServiceImpl.FAPI_OPENBANKING_BRAZIL_DIRECTORY_JWKS_URI)).thenReturn(DUMMY_JWKS_URI);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(new io.gravitee.am.model.jose.RSAKey()));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(clientService).create(any(), argThat(client -> client.getAccessTokenValiditySeconds() == FAPI_OPENBANKING_BRAZIL_DEFAULT_ACCESS_TOKEN_VALIDITY
                && client.getRefreshTokenValiditySeconds() == 1000
                && client.getIdTokenValiditySeconds() == 1100));

        assertNotNull(request.getTlsClientAuthSanDns());
        assertTrue(request.getTlsClientAuthSanDns().isEmpty());
        assertNotNull(request.getTlsClientAuthSanIp());
        assertTrue(request.getTlsClientAuthSanIp().isEmpty());
    }

    @Test
    public void create_Ciba_success() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));
        request.setRedirectUris(Optional.of(Arrays.asList(DUMMY_REDIRECTURI)));
        request.setGrantTypes(Optional.of(List.of(GrantType.CIBA_GRANT_TYPE)));

        request.setBackchannelAuthRequestSignAlg(Optional.of(JWSAlgorithm.RS256.getName()));
        request.setBackchannelClientNotificationEndpoint(Optional.of("https://127.0.0..1/ciba/notif"));
        request.setBackchannelTokenDeliveryMode(Optional.of(CIBADeliveryMode.POLL));

        when(domain.useCiba()).thenReturn(true);

        when(domain.useFapiBrazilProfile()).thenReturn(false);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        testObserver.assertValue(client -> !client.getBackchannelUserCodeParameter());
        testObserver.assertValue(client -> client.getBackchannelAuthRequestSignAlg() != null && client.getBackchannelAuthRequestSignAlg().equals(JWSAlgorithm.RS256.getName()));
        testObserver.assertValue(client -> client.getBackchannelClientNotificationEndpoint() != null && client.getBackchannelClientNotificationEndpoint().equals("https://127.0.0..1/ciba/notif"));
        testObserver.assertValue(client -> client.getBackchannelTokenDeliveryMode() != null && client.getBackchannelTokenDeliveryMode().equals(CIBADeliveryMode.POLL));
    }

    @Test
    public void create_Ciba_InvalidDeliveryMode() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));
        request.setRedirectUris(Optional.of(Arrays.asList(DUMMY_REDIRECTURI)));
        request.setGrantTypes(Optional.of(List.of(GrantType.CIBA_GRANT_TYPE)));

        request.setBackchannelAuthRequestSignAlg(Optional.of(JWSAlgorithm.RS256.getName()));
        request.setBackchannelClientNotificationEndpoint(Optional.of("https://127.0.0..1/ciba/notif"));
        request.setBackchannelTokenDeliveryMode(Optional.of("invalid"));

        when(domain.useCiba()).thenReturn(true);

        when(domain.useFapiBrazilProfile()).thenReturn(false);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_Ciba_MissingDeliveryMode() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.empty());
        request.setRequireParRequest(Optional.of(false));
        request.setRequestObjectEncryptionAlg(Optional.of(JWEAlgorithm.RSA_OAEP_256.getName()));
        request.setRequestObjectEncryptionEnc(Optional.of(EncryptionMethod.A256GCM.getName()));
        request.setJwksUri(Optional.of(DUMMY_JWKS_URI));
        request.setRedirectUris(Optional.of(Arrays.asList(DUMMY_REDIRECTURI)));
        request.setGrantTypes(Optional.of(List.of(GrantType.CIBA_GRANT_TYPE)));

        request.setBackchannelAuthRequestSignAlg(Optional.of(JWSAlgorithm.RS256.getName()));
        request.setBackchannelClientNotificationEndpoint(Optional.of("https://127.0.0..1/ciba/notif"));

        request.setBackchannelTokenDeliveryMode(null);

        when(domain.useCiba()).thenReturn(true);

        when(domain.useFapiBrazilProfile()).thenReturn(false);

        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(new JWKSet()));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void create_agentType_defaultsToAuthorizationCode() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getResponseTypes().contains("code") &&
                        client.getTokenEndpointAuthMethod().equals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        );
    }

    @Test
    public void create_agentType_stripsForbiddenGrantTypes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(
                GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS,
                GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN
        )));
        request.setResponseTypes(Optional.of(Arrays.asList("code")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.PASSWORD) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.REFRESH_TOKEN)
        );
    }

    @Test
    public void create_agentType_stripsImplicitResponseTypes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.AUTHORIZATION_CODE)));
        request.setResponseTypes(Optional.of(Arrays.asList("code", "token", "id_token", "id_token token")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getResponseTypes().contains("code") &&
                        !client.getResponseTypes().contains("token") &&
                        !client.getResponseTypes().contains("id_token") &&
                        !client.getResponseTypes().contains("id_token token")
        );
    }

    @Test
    public void create_agentType_allGrantTypesStrippedDefaultsToAuthCode() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.IMPLICIT, GrantType.PASSWORD)));
        request.setResponseTypes(Optional.of(Arrays.asList("token")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().size() == 1 &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getResponseTypes().contains("code")
        );
    }

    @Test
    public void create_agentType_strippedResponseTypesDefaultsToCodeWhenAuthCodeGrantPresent() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.AUTHORIZATION_CODE)));
        request.setResponseTypes(Optional.of(Arrays.asList("token")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getResponseTypes().size() == 1 &&
                        client.getResponseTypes().contains("code")
        );
    }

    @Test
    public void create_agentType_preservesExplicitTokenEndpointAuthMethod() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setTokenEndpointAuthMethod(Optional.of(ClientAuthenticationMethod.CLIENT_SECRET_POST));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getTokenEndpointAuthMethod().equals(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        );
    }

    @Test
    public void create_agentType_clientCredentialsOnlyPreserved() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.CLIENT_CREDENTIALS)));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().size() == 1 &&
                        client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)
        );
    }

    @Test
    public void create_nonAgentType_doesNotStripGrantTypes() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("web"));
        request.setGrantTypes(Optional.of(Arrays.asList(
                GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT, GrantType.PASSWORD
        )));
        request.setResponseTypes(Optional.of(Arrays.asList("code", "token")));

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("web") &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT) &&
                        client.getAuthorizedGrantTypes().contains(GrantType.PASSWORD) &&
                        client.getResponseTypes().contains("code") &&
                        client.getResponseTypes().contains("token")
        );
    }

    @Test
    public void create_agentType_nullResponseTypesDefaultsToCodeWithAuthCodeGrant() {
        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("agent"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.AUTHORIZATION_CODE)));
        // response_types not set (null)

        TestObserver<Client> testObserver = dcrService.create(request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getApplicationType().equals("agent") &&
                        client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getResponseTypes().contains("code")
        );
    }

    @Test
    public void patch_agentType_stripsForbiddenGrantTypesWhenApplicationTypeOmitted() {
        Client existingClient = new Client();
        existingClient.setApplicationType("agent");
        existingClient.setRedirectUris(Arrays.asList("https://example.com/callback"));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        // application_type NOT set in the request — simulates a typical PATCH
        request.setGrantTypes(Optional.of(Arrays.asList(
                GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS,
                GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN
        )));
        request.setResponseTypes(Optional.of(Arrays.asList("code")));

        TestObserver<Client> testObserver = dcrService.patch(existingClient, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.PASSWORD) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.REFRESH_TOKEN)
        );
    }

    @Test
    public void patch_agentType_stripsForbiddenResponseTypesWhenApplicationTypeOmitted() {
        Client existingClient = new Client();
        existingClient.setApplicationType("agent");
        existingClient.setRedirectUris(Arrays.asList("https://example.com/callback"));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.AUTHORIZATION_CODE)));
        request.setResponseTypes(Optional.of(Arrays.asList("code", "token", "id_token", "id_token token")));

        TestObserver<Client> testObserver = dcrService.patch(existingClient, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getResponseTypes().contains("code") &&
                        !client.getResponseTypes().contains("token") &&
                        !client.getResponseTypes().contains("id_token") &&
                        !client.getResponseTypes().contains("id_token token")
        );
    }

    @Test
    public void update_agentType_stripsForbiddenGrantTypesWhenApplicationTypeOmitted() {
        Client existingClient = new Client();
        existingClient.setApplicationType("agent");

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setGrantTypes(Optional.of(Arrays.asList(
                GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS,
                GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN
        )));
        request.setResponseTypes(Optional.of(Arrays.asList("code")));

        TestObserver<Client> testObserver = dcrService.update(existingClient, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client ->
                client.getAuthorizedGrantTypes().contains(GrantType.AUTHORIZATION_CODE) &&
                        client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.PASSWORD) &&
                        !client.getAuthorizedGrantTypes().contains(GrantType.REFRESH_TOKEN)
        );
    }

    @Test
    public void patch_allowsApplicationTypeChange() {
        Client existingClient = new Client();
        existingClient.setApplicationType("web");
        existingClient.setRedirectUris(Arrays.asList("https://example.com/callback"));

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setApplicationType(Optional.of("agent"));

        TestObserver<Client> testObserver = dcrService.patch(existingClient, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> {
            assertEquals("agent", client.getApplicationType());
            // When changing to 'agent' type, constraints are applied:
            // defaults grant_types, response_types, and token_endpoint_auth_method
            assertEquals(List.of(GrantType.AUTHORIZATION_CODE), client.getAuthorizedGrantTypes());
            assertEquals(List.of("code"), client.getResponseTypes());
            assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, client.getTokenEndpointAuthMethod());
            return true;
        });
    }

    @Test
    public void update_allowsApplicationTypeChange() {
        Client existingClient = new Client();
        existingClient.setApplicationType("agent");

        DynamicClientRegistrationRequest request = new DynamicClientRegistrationRequest();
        request.setRedirectUris(Optional.of(Arrays.asList("https://example.com/callback")));
        request.setApplicationType(Optional.of("web"));
        request.setGrantTypes(Optional.of(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT)));
        request.setResponseTypes(Optional.of(Arrays.asList("code", "token")));

        TestObserver<Client> testObserver = dcrService.update(existingClient, request, BASE_PATH).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(client -> {
            assertEquals("web", client.getApplicationType());
            // After switching from agent to web, previously-forbidden grant types are allowed
            assertTrue(client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT));
            return true;
        });
    }

    private RSAKey generateRSAKey() throws Exception {
        return new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
    }

    private String generateSoftwareStatement(RSAKey rsaJWK, JWSAlgorithm jwsAlg, Instant iat) throws Exception {
        JWSSigner signer = new RSASSASigner(rsaJWK);

        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("iat", iat.getEpochSecond());
        jsonObject.put("software_jwks_uri", DUMMY_JWKS_URI);
        final JSONArray redirectUris = new JSONArray();
        redirectUris.add(DUMMY_REDIRECTURI);
        jsonObject.put("software_redirect_uris", redirectUris);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(jwsAlg).keyID(rsaJWK.getKeyID()).build(),
                new Payload(jsonObject));

        jwsObject.sign(signer);

        return jwsObject.serialize();
    }

}
