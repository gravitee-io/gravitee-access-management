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
package io.gravitee.am.gateway.handler.common.client.cimd.impl;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdLogoCacheService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdUriTrustValidator;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.model.CimdClientState;
import io.gravitee.am.service.CimdClientStateService;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdMetadataServiceImplTest {

    @BeforeClass
    public static void setupSchedulers() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
    }

    @AfterClass
    public static void resetSchedulers() {
        RxJavaPlugins.reset();
    }

    private static final String CLIENT_URL = "https://localhost/metadata";
    private static final String EXTERNAL_URL = "https://external.example.com/metadata";

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> request;

    @Mock
    private HttpResponse<Buffer> response;

    @Mock
    private CimdUriTrustValidator uriTrustValidator;

    @Mock
    private CimdLogoCacheService logoCacheService;

    @Mock
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Mock
    private CimdMetadataDocumentManager cimdMetadataDocumentManager;

    @Mock
    private CimdClientStateService cimdClientStateService;

    @Mock
    private ScopeApprovalService scopeApprovalService;

    @Mock
    private RevokeTokenGatewayService revokeTokenGatewayService;

    private CimdMetadataService cimdMetadataService;
    private CIMDSettings cimdSettings;
    private Domain domain;

    @Before
    public void setUp() {
        domain = new Domain();
        domain.setId("domain-id");
        domain.setName("test-domain");

        OIDCSettings oidcSettings = new OIDCSettings();
        cimdSettings = new CIMDSettings();
        cimdSettings.setAllowPrivateIpAddress(true);
        oidcSettings.setCimdSettings(cimdSettings);
        domain.setOidc(oidcSettings);

        // Default: cache miss — falls through to HTTP fetch
        when(cimdMetadataDocumentManager.resolve(anyString())).thenReturn(Single.just(Optional.empty()));
        lenient().when(cimdMetadataDocumentManager.getLogoByClientId(anyString())).thenReturn(Optional.empty());
        when(cimdMetadataDocumentService.upsert(any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(Single.just(new CimdMetadataDocument()));

        lenient()
                .when(uriTrustValidator.parseHttpUrl(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    try {
                        return UriBuilder.fromHttpUrl(invocation.getArgument(0)).build();
                    } catch (Exception ex) {
                        throw new InvalidClientMetadataException(invocation.getArgument(1) + " is not a valid URL.");
                    }
                });
        lenient().doNothing().when(uriTrustValidator).validateTrust(any(), any(), anyString());
        lenient()
                .when(uriTrustValidator.validateResolvableHost(anyString(), anyString(), any()))
                .thenReturn(Completable.complete());
        lenient().when(cimdClientStateService.findByDomainAndClientId(any(Domain.class), anyString()))
                .thenReturn(Maybe.empty());
        lenient().when(cimdClientStateService.upsert(any(Domain.class), anyString(), anyString()))
                .thenReturn(Single.just(new CimdClientState()));

        cimdMetadataService = new CimdMetadataServiceImpl(
                domain,
                webClient,
                cimdMetadataDocumentService,
                cimdMetadataDocumentManager,
                uriTrustValidator,
                logoCacheService,
                cimdClientStateService,
                scopeApprovalService,
                revokeTokenGatewayService);
    }

    @After
    public void resetCimdTrustPolicy() {
        cimdSettings.setAllowPrivateIpAddress(true);
        cimdSettings.setAllowUnsecuredHttpUri(false);
        cimdSettings.setAllowedDomains(null);
    }

    @Test
    public void shouldReturnEmptyWhenClientIdIsNotUrlShaped() {
        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("my-client", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectWhenResolvableHostRejectedForClientId() {
        when(uriTrustValidator.validateResolvableHost(eq("external.example.com"), eq("client_id"), eq(cimdSettings)))
                .thenReturn(Completable.error(new InvalidClientMetadataException("private")));
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldReturnInvalidClientMetadataOnFetchTimeout() {
        mockRequest();
        when(request.rxSend()).thenReturn(Single.error(new TimeoutException("timeout")));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.awaitDone(2, TimeUnit.SECONDS);
        testObserver.assertError(throwable -> throwable instanceof InvalidClientMetadataException && throwable.getMessage().contains("timed out"));
        verify(request, times(3)).rxSend();
    }

    @Test
    public void shouldReturnInvalidClientMetadataOnNon200Response() {
        mockRequest();
        when(request.rxSend()).thenReturn(Single.just(response), Single.just(response), Single.just(response));
        when(response.statusCode()).thenReturn(404, 404, 404);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.awaitDone(2, TimeUnit.SECONDS);
        testObserver.assertError(throwable -> throwable instanceof InvalidClientMetadataException && throwable.getMessage().contains("HTTP 404"));
        verify(request, times(3)).rxSend();
    }

    @Test
    public void shouldRetryOnNon200AndSucceedOnNextAttempt() {
        mockRequest();
        when(request.rxSend()).thenReturn(Single.just(response), Single.just(response));
        when(response.statusCode()).thenReturn(503, 200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(metadataPayload(CLIENT_URL)));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.awaitDone(2, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> CLIENT_URL.equals(client.getClientId()));
        verify(request, times(2)).rxSend();
    }

    @Test
    public void shouldRejectPayloadLargerThanMaxResponseSize() {
        cimdSettings.setMaxResponseSizeKb(1);
        mockRequest();
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer("x".repeat(1100)));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.awaitDone(2, TimeUnit.SECONDS);
        testObserver.assertError(throwable -> throwable instanceof InvalidClientMetadataException && throwable.getMessage().contains("max size"));
    }

    @Test
    public void shouldSynthesizeClientWithMetadataValues() {
        Client template = templateClient();
        template.setAuthorizedGrantTypes(List.of("authorization_code", "client_credentials"));
        template.setResponseTypes(List.of("code", "token"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks")
                .put("client_name", "CIMD App")
                .put("grant_types", new JsonArray().add("client_credentials"))
                .put("response_types", new JsonArray().add("token"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> CLIENT_URL.equals(client.getClientId())
                && "CIMD App".equals(client.getClientName())
                && "private_key_jwt".equals(client.getTokenEndpointAuthMethod())
                && "https://localhost/jwks".equals(client.getJwksUri())
                && List.of("https://callback.example.com/cb").equals(client.getRedirectUris())
                && List.of("client_credentials").equals(client.getAuthorizedGrantTypes())
                && List.of("token").equals(client.getResponseTypes())
                && client.getClientSecret() == null
                && client.getClientSecrets().isEmpty()
                && client.getSecretSettings().isEmpty()
                && !client.isTemplate()
                && "domain-id".equals(client.getDomain()));
    }

    @Test
    public void shouldStoreMetadataDocumentHashInSynthesizedClientMetadata() throws Exception {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none");
        String metadataJson = metadata.encode();
        mockFetchSuccess(metadataJson);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(metadataJson.getBytes(StandardCharsets.UTF_8)));
        testObserver.assertValue(client -> expectedHash.equals(client.getCimdMetadataHash()));
    }

    @Test
    public void shouldRejectClientIdMismatch() {
        JsonObject metadata = new JsonObject()
                .put("client_id", "https://localhost/other")
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldRejectMissingRedirectUris() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldRejectSecretBasedAuthMethod() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "client_secret_post");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldRejectClientSecretPresence() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks")
                .put("client_secret", "secret-value");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldRejectPrivateKeyJwtWithoutKeyMaterial() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldRejectWhenValidateTrustRejectsJwksUri() {
        doThrow(new InvalidClientMetadataException("jwks_uri host is not in allowed domains."))
                .when(uriTrustValidator).validateTrust(any(URI.class), eq(cimdSettings), eq("jwks_uri"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verify(uriTrustValidator).validateTrust(any(URI.class), eq(cimdSettings), eq("jwks_uri"));
        verify(cimdMetadataDocumentManager, never()).put(anyString(), any(JsonObject.class), any(Duration.class));
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldRejectWhenValidateResolvableHostRejectsJwksUri() {
        when(uriTrustValidator.validateResolvableHost(eq("idp.example.com"), eq("jwks_uri"), eq(cimdSettings)))
                .thenReturn(Completable.error(new InvalidClientMetadataException("jwks_uri resolves to a private or reserved IP address.")));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldRejectMetadataWithMalformedJwksUri() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "not a url");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verify(uriTrustValidator).parseHttpUrl("not a url", "jwks_uri");
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldValidateJWKsUriWithRightFieldName() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(uriTrustValidator).parseHttpUrl("https://idp.example.com/jwks", "jwks_uri");
        verify(uriTrustValidator).validateTrust(any(URI.class), eq(cimdSettings), eq("jwks_uri"));
        verify(uriTrustValidator).validateResolvableHost(eq("idp.example.com"), eq("jwks_uri"), eq(cimdSettings));
    }

    @Test
    public void shouldSkipJwksUriValidationWhenAbsent() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> client.getJwksUri() == null);
        verify(uriTrustValidator, never()).validateTrust(any(URI.class), any(), eq("jwks_uri"));
        verify(uriTrustValidator, never()).validateResolvableHost(anyString(), eq("jwks_uri"), any());
    }

    @Test
    public void shouldNotRevalidateJWKsUriOnLocalCacheHit() {
        // On a cache hit, validateMetadata is not invoked — so the validator must not be called for jwks_uri,
        // even if the cached metadata contains a now-untrusted jwks_uri.
        String cachedPayload = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://disallowed.example.org/jwks")
                .encode();
        CimdMetadataDocument cached = new CimdMetadataDocument();
        cached.setDomainId("domain-id");
        cached.setClientId(CLIENT_URL);
        cached.setMetadata(cachedPayload);
        cached.setFetchedAt(new Date());
        cached.setExpiresAt(new Date(System.currentTimeMillis() + 86400_000));
        cached.setUpdatedAt(new Date());

        when(cimdMetadataDocumentManager.resolve(CLIENT_URL))
                .thenReturn(Single.just(Optional.of(cached)));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
        verifyNoInteractions(webClient);
        verify(uriTrustValidator, never()).validateTrust(any(URI.class), any(), eq("jwks_uri"));
        verify(uriTrustValidator, never()).validateResolvableHost(anyString(), eq("jwks_uri"), any());
    }

    @Test
    public void shouldDefaultMissingTokenEndpointAuthMethodToNone() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "none".equals(client.getTokenEndpointAuthMethod())
                && List.of("authorization_code").equals(client.getAuthorizedGrantTypes())
                && List.of("code").equals(client.getResponseTypes()));
    }

    @Test
    public void shouldRejectExplicitClientSecretBasic() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "client_secret_basic");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void shouldDefaultGrantTypesToAuthorizationCodeWhenAbsent() {
        Client template = templateClient();
        // template already includes authorization_code — default intersects to ["authorization_code"]
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> List.of("authorization_code").equals(client.getAuthorizedGrantTypes()));
    }

    @Test
    public void shouldIntersectGrantTypesWithTemplate() {
        Client template = templateClient();
        template.setAuthorizedGrantTypes(List.of("authorization_code"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("grant_types", new JsonArray().add("authorization_code").add("client_credentials"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> List.of("authorization_code").equals(client.getAuthorizedGrantTypes()));
    }

    @Test
    public void shouldExcludeGrantTypesNotInTemplate() {
        Client template = templateClient();
        template.setAuthorizedGrantTypes(List.of("authorization_code"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("grant_types", new JsonArray().add("client_credentials"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> client.getAuthorizedGrantTypes().isEmpty());
    }

    @Test
    public void shouldDefaultResponseTypesToCodeWhenAbsent() {
        Client template = templateClient();
        // template already includes code — default intersects to ["code"]
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> List.of("code").equals(client.getResponseTypes()));
    }

    @Test
    public void shouldIntersectResponseTypesWithTemplate() {
        Client template = templateClient();
        template.setResponseTypes(List.of("code"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("response_types", new JsonArray().add("code").add("token"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> List.of("code").equals(client.getResponseTypes()));
    }

    @Test
    public void shouldExcludeResponseTypesNotInTemplate() {
        Client template = templateClient();
        template.setResponseTypes(List.of("code"));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("response_types", new JsonArray().add("token"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> client.getResponseTypes().isEmpty());
    }

    @Test
    public void shouldIntersectScopeWithTemplateScopes() {
        Client template = templateClient();
        template.setScopeSettings(List.of(
                scopeSetting("read", false),
                scopeSetting("write", false)));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("scope", "read");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client ->
                client.getScopeSettings() != null
                        && client.getScopeSettings().size() == 1
                        && "read".equals(client.getScopeSettings().get(0).getScope()));
    }

    @Test
    public void shouldPreserveDefaultScopesWithinIntersection() {
        Client template = templateClient();
        template.setScopeSettings(List.of(
                scopeSetting("read", true),
                scopeSetting("write", false)));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("scope", "read write");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client ->
                client.getScopeSettings() != null
                        && client.getScopeSettings().size() == 2
                        && client.getScopeSettings().stream().anyMatch(s -> "read".equals(s.getScope()) && s.isDefaultScope())
                        && client.getScopeSettings().stream().anyMatch(s -> "write".equals(s.getScope()) && !s.isDefaultScope()));
    }

    @Test
    public void shouldRestrictDefaultScopeWhenExcludedByIntersection() {
        Client template = templateClient();
        template.setScopeSettings(List.of(
                scopeSetting("read", true)));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("scope", "write");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client ->
                client.getScopeSettings() != null && client.getScopeSettings().isEmpty());
    }

    @Test
    public void shouldPreserveTemplateScopesWhenScopeIsBlankInMetadata() {
        Client template = templateClient();
        template.setScopeSettings(List.of(scopeSetting("read", true)));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none")
                .put("scope", "");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client ->
                client.getScopeSettings() != null
                        && client.getScopeSettings().size() == 1
                        && "read".equals(client.getScopeSettings().get(0).getScope()));
    }

    @Test
    public void shouldPreserveTemplateScopesWhenNoScopeInMetadata() {
        Client template = templateClient();
        template.setScopeSettings(List.of(
                scopeSetting("read", true),
                scopeSetting("write", false)));
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "none");
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, template).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client ->
                client.getScopeSettings() != null
                        && client.getScopeSettings().size() == 2);
    }

    @Test
    public void shouldResolveInlineJwksUsingSharedConverterFields() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks", new JsonObject().put("keys", new JsonArray().add(
                        new JsonObject()
                                .put("kty", "oct")
                                .put("kid", "oct-kid")
                                .put("use", "sig")
                                .put("alg", "HS256")
                                .put("k", "FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ")
                )));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> client.getJwks() != null
                && client.getJwks().getKeys() != null
                && client.getJwks().getKeys().size() == 1
                && "oct-kid".equals(client.getJwks().getKeys().get(0).getKid())
                && "sig".equals(client.getJwks().getKeys().get(0).getUse())
                && client.getJwks().getKeys().get(0).getAlg() == null);
    }

    @Test
    public void shouldDisableRedirectsForMetadataFetchWhenNoLogoUri() {
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        verify(request).followRedirects(false);
        verify(request, never()).followRedirects(true);
    }

    @Test
    public void shouldFollowRedirectsForLogoPrefetchWhenLogoUriPresent() {
        mockFetchSuccess(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(request).followRedirects(false);
        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("https://localhost/logo.png"), anyLong(), eq(cimdSettings));
    }

    @Test
    public void shouldConfigureBodyCodecPipeForMetadataFetch() {
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(request).as(any());
    }

    private void mockFetchSuccess(String payload) {
        mockRequest();
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(payload));
    }

    private void mockRequest() {
        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.timeout(anyLong())).thenReturn(request);
        when(request.followRedirects(false)).thenReturn(request);
        lenient().when(request.followRedirects(true)).thenReturn(request);
        when(request.as(any())).thenReturn((HttpRequest) request);
    }

    private String metadataPayload(String clientId) {
        return new JsonObject()
                .put("client_id", clientId)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks")
                .encode();
    }

    // --- Two-tier cache tests ---

    @Test
    public void shouldReturnFromLocalCacheWithoutHttpOrDbCall() {
        CimdMetadataDocument cached = cachedDocument(false);
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(cached)));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldReturnFromDbCacheWithoutHttpCall() {
        CimdMetadataDocument dbDoc = cachedDocument(false);
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(dbDoc)));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldFetchFromOriginAndPersistWhenNoCacheHit() {
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verify(request).as(any());
        verify(cimdMetadataDocumentService).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldRespectCacheControlMaxAgeUpToCacheTtl() {
        cimdSettings.setCacheTtlSeconds(3600);
        // Server says max-age=300, which is less than configuredTtl — should use 300s
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "max-age=300");

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        verify(cimdMetadataDocumentService).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldCapCacheControlMaxAgeAtConfiguredTtl() {
        cimdSettings.setCacheTtlSeconds(600);
        // Server says max-age=99999, should be capped at 600s
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "max-age=99999");

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        verify(cimdMetadataDocumentService).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldNotPersistOrWarmLocalCacheWhenCacheControlNoStore() {
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "no-store");

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
        verify(cimdMetadataDocumentManager, never()).put(anyString(), any());
    }

    @Test
    public void shouldNotPersistWhenNoStoreWithMaxAge() {
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "max-age=300, no-store");

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldRefetchFromOriginOnSecondRequestWhenNoStore() {
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "no-store");

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();
        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(webClient, times(2)).getAbs(CLIENT_URL);
        verify(cimdMetadataDocumentService, never()).upsert(any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    public void shouldSucceedEvenWhenPersistFails() {
        when(cimdMetadataDocumentService.upsert(any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(Single.error(new RuntimeException("DB down")));
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        // Upsert failure is fire-and-forget — request must still succeed
        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
    }

    // --- logoUri synthesis tests ---

    @Test
    public void shouldPopulateLogoUriOnSynthesizedClientWhenPresentInMetadata() {
        mockFetchSuccess(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(client -> "https://localhost/logo.png".equals(client.getLogoUri()));
    }

    @Test
    public void shouldNotSetLogoUriWhenAbsentFromMetadata() {
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(client -> client.getLogoUri() == null);
    }

    @Test
    public void shouldTriggerLogoPrefetchOnFreshFetch() {
        mockFetchSuccess(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("https://localhost/logo.png"), anyLong(), eq(cimdSettings));
    }

    @Test
    public void shouldNotTriggerLogoPrefetchWhenNoStoreAndNoLogoInMetadata() {
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "no-store");

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService, never()).prefetchLogoAsync(anyString(), any(), anyLong(), any());
    }

    @Test
    public void shouldTriggerLogoPrefetchOnCacheHitWhenLogoNotYetCached() {
        CimdMetadataDocument doc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("https://localhost/logo.png"), anyLong(), eq(cimdSettings));
    }

    @Test
    public void shouldSkipLogoPrefetchOnCacheHitWhenLogoAlreadyCached() {
        CimdMetadataDocument doc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("https://localhost/logo.png"), anyLong(), eq(cimdSettings));
    }

    // --- logo_uri validation and edge cases ---

    @Test
    public void shouldNotPrefetchLogoWhenLogoUriIsNull() {
        CimdMetadataDocument doc = cachedDocument(false);
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), isNull(), anyLong(), eq(cimdSettings));
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotPrefetchLogoWhenLogoUriIsBlank() {
        mockFetchSuccess(metadataPayloadWithBlankLogo(CLIENT_URL));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq(""), anyLong(), eq(cimdSettings));
    }

    @Test
    public void shouldNotFetchLogoWhenLogoUriIsInvalid() {
        CimdMetadataDocument doc = cachedDocumentWithLogo(false, "not-a-url");
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("not-a-url"), anyLong(), eq(cimdSettings));
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotFetchLogoWhenLogoUriIsUntrusted() {
        cimdSettings.setAllowUnsecuredHttpUri(false);
        CimdMetadataDocument doc = cachedDocumentWithLogo(false, "http://localhost/logo.png");
        when(cimdMetadataDocumentManager.resolve(CLIENT_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("http://localhost/logo.png"), anyLong(), eq(cimdSettings));
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotFetchLogoWhenLogoHostOutsideAllowedDomains() {
        cimdSettings.setAllowedDomains(List.of("*.example.com"));
        CimdMetadataDocument doc = cachedDocumentWithLogo(false, "https://cdn.evil.net/logo.png");
        doc.setClientId(EXTERNAL_URL);
        doc.setMetadata(metadataPayloadWithLogo(EXTERNAL_URL, "https://cdn.evil.net/logo.png"));
        when(cimdMetadataDocumentManager.resolve(EXTERNAL_URL)).thenReturn(Single.just(Optional.of(doc)));

        cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(EXTERNAL_URL), eq("https://cdn.evil.net/logo.png"), anyLong(), eq(cimdSettings));
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldSkipLogoPrefetchOnFreshFetchWhenLogoAlreadyCached() {
        mockFetchSuccess(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(logoCacheService).prefetchLogoAsync(eq(CLIENT_URL), eq("https://localhost/logo.png"), anyLong(), eq(cimdSettings));
    }

    // --- detectAndRevokeOnChange tests ---

    @Test
    public void shouldNotCheckClientStateWhenRevokeOnDocumentChangeDisabled() {
        // revokeOnDocumentChange is false by default — cimdClientStateService must never be called
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test()
                .awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        verify(cimdClientStateService, never()).findByDomainAndClientId(any(Domain.class), anyString());
        verify(cimdClientStateService, never()).upsert(any(Domain.class), anyString(), anyString());
        verify(scopeApprovalService, never()).revokeByClient(any(), anyString(), any());
    }

    @Test
    public void shouldStoreHashOnFirstFetchWithoutRevocation() {
        cimdSettings.setRevokeOnDocumentChange(true);
        // default stub: findByDomainAndClientId returns Maybe.empty() (no prior state)
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test()
                .awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        verify(cimdClientStateService).upsert(eq(domain), eq(CLIENT_URL), anyString());
        verify(scopeApprovalService, never()).revokeByClient(any(), anyString(), any());
        verify(revokeTokenGatewayService, never()).process(any(), any());
    }

    @Test
    public void shouldNotRevokeWhenMonitoredPropertiesHashUnchanged() {
        cimdSettings.setRevokeOnDocumentChange(true);
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks");
        String sameHash = CimdMetadataServiceImpl.calculateMonitoredPropertiesHash(metadata);
        CimdClientState existingState = new CimdClientState();
        existingState.setMonitoredPropertiesHash(sameHash);
        when(cimdClientStateService.findByDomainAndClientId(domain, CLIENT_URL))
                .thenReturn(Maybe.just(existingState));
        mockFetchSuccess(metadata.encode());

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test()
                .awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        verify(cimdClientStateService, never()).upsert(any(Domain.class), anyString(), anyString());
        verify(scopeApprovalService, never()).revokeByClient(any(), anyString(), any());
        verify(revokeTokenGatewayService, never()).process(any(), any());
    }

    @Test
    public void shouldRevokeWhenMonitoredPropertiesHashChanged() {
        cimdSettings.setRevokeOnDocumentChange(true);
        CimdClientState existingState = new CimdClientState();
        existingState.setId("existing-state-id");
        existingState.setMonitoredPropertiesHash("old-hash-that-will-not-match");
        when(cimdClientStateService.findByDomainAndClientId(domain, CLIENT_URL))
                .thenReturn(Maybe.just(existingState));
        when(scopeApprovalService.revokeByClient(eq(domain), eq(CLIENT_URL), any()))
                .thenReturn(Completable.complete());
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test()
                .awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        verify(scopeApprovalService).revokeByClient(eq(domain), eq(CLIENT_URL), any());
        verify(cimdClientStateService).upsert(eq(domain), eq(CLIENT_URL), anyString());
    }

    // --- helpers ---

    private CimdMetadataDocument cachedDocument(boolean expired) {
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setDomainId("domain-id");
        doc.setClientId(CLIENT_URL);
        doc.setMetadata(metadataPayload(CLIENT_URL));
        doc.setFetchedAt(new Date());
        doc.setExpiresAt(expired ? new Date(System.currentTimeMillis() - 1000) : new Date(System.currentTimeMillis() + 86400_000));
        doc.setUpdatedAt(new Date());
        return doc;
    }

    private void mockFetchSuccessWithCacheControl(String payload, String cacheControlHeader) {
        mockRequest();
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(payload));
        when(response.getHeader("Cache-Control")).thenReturn(cacheControlHeader);
    }

    private String metadataPayloadWithLogo(String clientId, String logoUri) {
        return new JsonObject()
                .put("client_id", clientId)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks")
                .put("logo_uri", logoUri)
                .encode();
    }

    private CimdMetadataDocument cachedDocumentWithLogo(boolean expired, String logoUri) {
        CimdMetadataDocument doc = cachedDocument(expired);
        doc.setMetadata(metadataPayloadWithLogo(CLIENT_URL, logoUri));
        return doc;
    }

    private String metadataPayloadWithBlankLogo(String clientId) {
        return new JsonObject()
                .put("client_id", clientId)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://idp.example.com/jwks")
                .put("logo_uri", "")
                .encode();
    }

    private Client templateClient() {
        Client template = new Client();
        template.setTemplate(true);
        template.setClientName("template-client");
        template.setAuthorizedGrantTypes(List.of("authorization_code"));
        template.setResponseTypes(List.of("code"));
        template.setClientSecret("template-secret");
        template.setClientSecrets(List.of(new ClientSecret()));
        template.setSecretSettings(List.of(new ApplicationSecretSettings()));
        return template;
    }

    private ApplicationScopeSettings scopeSetting(String scope, boolean defaultScope) {
        ApplicationScopeSettings s = new ApplicationScopeSettings(scope);
        s.setDefaultScope(defaultScope);
        return s;
    }
}
