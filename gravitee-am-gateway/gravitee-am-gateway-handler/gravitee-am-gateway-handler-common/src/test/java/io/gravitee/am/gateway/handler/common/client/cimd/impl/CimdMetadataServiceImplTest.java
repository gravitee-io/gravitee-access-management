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

import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdMetadataServiceImplTest {

    private static final String CLIENT_URL = "https://localhost/metadata";

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> request;

    @Mock
    private HttpResponse<Buffer> response;

    @Mock
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Mock
    private CimdMetadataDocumentManager cimdMetadataDocumentManager;

    private CimdMetadataService cimdMetadataService;
    private CIMDSettings cimdSettings;

    @Before
    public void setUp() {
        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setName("test-domain");

        OIDCSettings oidcSettings = new OIDCSettings();
        cimdSettings = new CIMDSettings();
        cimdSettings.setAllowPrivateIpAddress(true);
        oidcSettings.setCimdSettings(cimdSettings);
        domain.setOidc(oidcSettings);

        // Default: local cache miss, DB miss — falls through to HTTP fetch
        when(cimdMetadataDocumentManager.get(anyString())).thenReturn(Optional.empty());
        when(cimdMetadataDocumentService.findByDomainAndClientId(anyString(), anyString())).thenReturn(Maybe.empty());
        when(cimdMetadataDocumentService.upsert(any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(Single.just(new CimdMetadataDocument()));

        cimdMetadataService = new CimdMetadataServiceImpl(domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager);
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
    public void shouldRejectHttpWhenUnsecuredHttpIsDisabled() {
        cimdSettings.setAllowUnsecuredHttpUri(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("http://localhost/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldAcceptHttpWhenUnsecuredHttpIsEnabled() {
        cimdSettings.setAllowUnsecuredHttpUri(true);
        mockFetchSuccess(metadataPayload("http://localhost/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("http://localhost/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "http://localhost/metadata".equals(client.getClientId()));
    }

    @Test
    public void shouldAllowIpLiteralWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);
        mockFetchSuccess(metadataPayload("https://127.0.0.1/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://127.0.0.1/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "https://127.0.0.1/metadata".equals(client.getClientId()));
    }

    @Test
    public void shouldAllowUnresolvableHostnameWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);
        mockFetchSuccess(metadataPayload("https://non-resolvable.example.invalid/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://non-resolvable.example.invalid/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "https://non-resolvable.example.invalid/metadata".equals(client.getClientId()));
    }

    @Test
    public void shouldAllowPrivateIpWhenPrivateIpIsEnabled() {
        cimdSettings.setAllowPrivateIpAddress(true);
        mockFetchSuccess(metadataPayload("https://127.0.0.1/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://127.0.0.1/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "https://127.0.0.1/metadata".equals(client.getClientId()));
    }

    @Test
    public void shouldRejectHostOutsideAllowedDomains() {
        cimdSettings.setAllowedDomains(List.of("*.example.com"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldAllowHostInsideAllowedDomains() {
        cimdSettings.setAllowedDomains(List.of("localhost"));
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> CLIENT_URL.equals(client.getClientId()));
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
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks")
                .put("client_name", "CIMD App")
                .put("grant_types", new JsonArray().add("client_credentials"))
                .put("response_types", new JsonArray().add("token"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

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
    public void shouldDefaultMissingTokenEndpointAuthMethodToClientSecretBasicAndReject() {
        JsonObject metadata = new JsonObject()
                .put("client_id", CLIENT_URL)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"));
        mockFetchSuccess(metadata.encode());

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
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
    public void shouldPassFollowRedirectsAsFalse() {
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertComplete();
        verify(request).followRedirects(false);
        verify(request, never()).followRedirects(true);
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
        when(request.as(any())).thenReturn((HttpRequest) request);
    }

    private String metadataPayload(String clientId) {
        return new JsonObject()
                .put("client_id", clientId)
                .put("redirect_uris", new JsonArray().add("https://callback.example.com/cb"))
                .put("token_endpoint_auth_method", "private_key_jwt")
                .put("jwks_uri", "https://localhost/jwks")
                .encode();
    }

    // --- Two-tier cache tests ---

    @Test
    public void shouldReturnFromLocalCacheWithoutHttpOrDbCall() {
        CimdMetadataDocument cached = cachedDocument(false);
        when(cimdMetadataDocumentManager.get(CLIENT_URL)).thenReturn(Optional.of(cached));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verifyNoInteractions(webClient);
        verify(cimdMetadataDocumentService, never()).findByDomainAndClientId(anyString(), anyString());
    }

    @Test
    public void shouldReturnFromDbCacheWithoutHttpCall() {
        CimdMetadataDocument dbDoc = cachedDocument(false);
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verifyNoInteractions(webClient);
        verify(cimdMetadataDocumentManager).put(CLIENT_URL, dbDoc);
    }

    @Test
    public void shouldFetchFromOriginWhenDbDocumentIsExpired() {
        CimdMetadataDocument expiredDoc = cachedDocument(true);
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(expiredDoc));
        mockFetchSuccess(metadataPayload(CLIENT_URL));

        TestObserver<Client> obs = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        verify(request).as(any());
        verify(cimdMetadataDocumentService).upsert(any(), anyString(), anyString(), any(Duration.class));
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
}
