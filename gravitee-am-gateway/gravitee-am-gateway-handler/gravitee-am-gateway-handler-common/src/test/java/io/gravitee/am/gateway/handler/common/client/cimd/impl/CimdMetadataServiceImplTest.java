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

import io.gravitee.am.gateway.handler.common.client.cimd.CachedLogo;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.web.HostSsrfGuard;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
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
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Mock
    private CimdMetadataDocumentManager cimdMetadataDocumentManager;

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

        // Default: local cache miss, DB miss — falls through to HTTP fetch
        when(cimdMetadataDocumentManager.get(anyString())).thenReturn(Optional.empty());
        when(cimdMetadataDocumentManager.getLogoByClientId(anyString())).thenReturn(Optional.empty());
        when(cimdMetadataDocumentService.findByDomainAndClientId(anyString(), anyString())).thenReturn(Maybe.empty());
        when(cimdMetadataDocumentService.upsert(any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(Single.just(new CimdMetadataDocument()));

        HostSsrfGuard hostSsrfGuard = new HostSsrfGuard(host -> new java.net.InetAddress[]{java.net.InetAddress.getByName("8.8.8.8")});
        cimdMetadataService = new CimdMetadataServiceImpl(domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager,
                hostSsrfGuard);
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
    public void shouldRejectLoopbackIpLiteralWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://127.0.0.1/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
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
    public void shouldRejectPrivateClassAIpWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://10.0.0.1/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectPrivateClassBIpWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://172.16.0.1/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectPrivateClassCIpWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://192.168.1.1/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectLinkLocalIpv4WhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://169.254.169.254/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectLoopbackIpv6WhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://[::1]/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldRejectLinkLocalIpv6WhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://[fe80::1]/metadata", templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldAllowPublicIpWhenPrivateIpIsDisabled() {
        cimdSettings.setAllowPrivateIpAddress(false);
        mockFetchSuccess(metadataPayload("https://8.8.8.8/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://8.8.8.8/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(client -> "https://8.8.8.8/metadata".equals(client.getClientId()));
    }

    @Test
    public void shouldAllowPrivateIpRangesWhenPrivateIpIsEnabled() {
        cimdSettings.setAllowPrivateIpAddress(true);
        mockFetchSuccess(metadataPayload("https://10.0.0.1/metadata"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient("https://10.0.0.1/metadata", templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldRejectLogoUriWithPrivateIpWhenPrivateIpIsDisabled() {
        // Use a non-private client_id URL so Phase 1 does not block the main request.
        // The logo_uri has a private IP — prefetchLogoAsync must not fetch it.
        cimdSettings.setAllowPrivateIpAddress(false);
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://192.168.0.1/logo.png");
        dbDoc.setClientId(EXTERNAL_URL);
        dbDoc.setMetadata(metadataPayloadWithLogo(EXTERNAL_URL, "https://192.168.0.1/logo.png"));
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", EXTERNAL_URL))
                .thenReturn(Maybe.just(dbDoc));
        when(cimdMetadataDocumentManager.get(EXTERNAL_URL)).thenReturn(Optional.empty());
        when(cimdMetadataDocumentManager.getLogoByClientId(EXTERNAL_URL)).thenReturn(Optional.empty());

        cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldSkipLogoPrefetchWhenLogoHostnameResolvesToPrivateIp() {
        cimdSettings.setAllowPrivateIpAddress(false);
        HostSsrfGuard rejectingGuard = new HostSsrfGuard(host ->
                new java.net.InetAddress[]{java.net.InetAddress.getLoopbackAddress()});
        cimdMetadataService = new CimdMetadataServiceImpl(
                domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager, rejectingGuard);
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://branding.vendor.example/logo.png");
        dbDoc.setClientId(EXTERNAL_URL);
        dbDoc.setMetadata(metadataPayloadWithLogo(EXTERNAL_URL, "https://branding.vendor.example/logo.png"));
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", EXTERNAL_URL))
                .thenReturn(Maybe.just(dbDoc));
        when(cimdMetadataDocumentManager.get(EXTERNAL_URL)).thenReturn(Optional.empty());
        when(cimdMetadataDocumentManager.getLogoByClientId(EXTERNAL_URL)).thenReturn(Optional.empty());

        cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldSkipLogoPrefetchAfterOriginFetchWhenLogoHostnameResolvesToPrivateIp() throws Exception {
        cimdSettings.setAllowPrivateIpAddress(false);
        HostSsrfGuard selectiveGuard = new HostSsrfGuard(host -> {
            if ("external.example.com".equalsIgnoreCase(host)) {
                return new java.net.InetAddress[]{java.net.InetAddress.getByName("8.8.8.8")};
            }
            return new java.net.InetAddress[]{java.net.InetAddress.getLoopbackAddress()};
        });
        cimdMetadataService = new CimdMetadataServiceImpl(
                domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager, selectiveGuard);
        mockFetchSuccess(metadataPayloadWithLogo(EXTERNAL_URL, "https://branding.vendor.example/logo.png"));

        cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test().assertComplete();

        verify(request, times(1)).rxSend();
        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldRejectWhenHostSsrfGuardRejectsHostname() {
        HostSsrfGuard rejectingGuard = new HostSsrfGuard(host ->
                new java.net.InetAddress[]{java.net.InetAddress.getLoopbackAddress()});
        cimdMetadataService = new CimdMetadataServiceImpl(
                domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager, rejectingGuard);
        cimdSettings.setAllowPrivateIpAddress(false);

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldAllowWhenHostSsrfGuardAllowsHostname() throws Exception {
        java.net.InetAddress publicIp = java.net.InetAddress.getByName("8.8.8.8");
        HostSsrfGuard allowingGuard = new HostSsrfGuard(host -> new java.net.InetAddress[]{publicIp});
        cimdMetadataService = new CimdMetadataServiceImpl(
                domain, webClient, cimdMetadataDocumentService, cimdMetadataDocumentManager, allowingGuard);
        cimdSettings.setAllowPrivateIpAddress(false);
        mockFetchSuccess(metadataPayload(EXTERNAL_URL));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldRejectHostOutsideAllowedDomains() {
        cimdSettings.setAllowedDomains(List.of("*.example.com"));

        TestObserver<Client> testObserver = cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test();

        testObserver.assertError(e -> e instanceof InvalidClientMetadataException
                && e.getMessage().contains("client_id")
                && e.getMessage().contains("allowed domains"));
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
        verify(request).followRedirects(true);
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
        when(request.followRedirects(true)).thenReturn(request);
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

        verify(cimdMetadataDocumentManager).putLogo(eq(CLIENT_URL), any(CachedLogo.class));
    }

    @Test
    public void shouldNotTriggerLogoPrefetchWhenNoStoreAndNoLogoInMetadata() {
        mockFetchSuccessWithCacheControl(metadataPayload(CLIENT_URL), "no-store");

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldTriggerLogoPrefetchOnDbHitWhenLogoNotYetCached() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        when(cimdMetadataDocumentManager.getLogoByClientId(CLIENT_URL)).thenReturn(Optional.empty());
        mockLogoFetch();

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager).putLogo(eq(CLIENT_URL), any(CachedLogo.class));
    }

    @Test
    public void shouldSkipLogoPrefetchOnDbHitWhenLogoAlreadyCached() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        when(cimdMetadataDocumentManager.getLogoByClientId(CLIENT_URL))
                .thenReturn(Optional.of(new CachedLogo(new byte[]{1, 2, 3}, "image/png", 3600L)));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    // --- logo_uri validation and edge cases ---

    @Test
    public void shouldNotPrefetchLogoWhenLogoUriIsNull() {
        CimdMetadataDocument dbDoc = cachedDocument(false);
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotPrefetchLogoWhenLogoUriIsBlank() {
        mockFetchSuccess(metadataPayloadWithBlankLogo(CLIENT_URL));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldNotFetchLogoWhenLogoUriIsInvalid() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "not-a-url");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotFetchLogoWhenLogoUriIsUntrusted() {
        cimdSettings.setAllowUnsecuredHttpUri(false);
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "http://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldNotFetchLogoWhenLogoHostOutsideAllowedDomains() {
        cimdSettings.setAllowedDomains(List.of("*.example.com"));
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://cdn.evil.net/logo.png");
        dbDoc.setClientId(EXTERNAL_URL);
        dbDoc.setMetadata(metadataPayloadWithLogo(EXTERNAL_URL, "https://cdn.evil.net/logo.png"));
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", EXTERNAL_URL))
                .thenReturn(Maybe.just(dbDoc));
        when(cimdMetadataDocumentManager.get(EXTERNAL_URL)).thenReturn(Optional.empty());
        when(cimdMetadataDocumentManager.getLogoByClientId(EXTERNAL_URL)).thenReturn(Optional.empty());

        cimdMetadataService.resolveClient(EXTERNAL_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void shouldSkipLogoPrefetchOnFreshFetchWhenLogoAlreadyCached() {
        when(cimdMetadataDocumentManager.getLogoByClientId(CLIENT_URL))
                .thenReturn(Optional.of(new CachedLogo(new byte[]{1, 2, 3}, "image/png", 3600L)));
        mockFetchSuccess(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldNotCacheLogoWhenResponseIsNotOk() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        mockLogoFetch();
        when(response.statusCode()).thenReturn(404);

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldNotCacheLogoWhenBodyIsEmpty() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        mockLogoFetch();
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer());

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldNotCacheLogoWhenBodyExceedsMaxSize() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        mockLogoFetch();
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[256 * 1024 + 1]));

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void shouldUseContentTypeHeaderWhenPresentForLogo() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        mockLogoFetch();
        when(response.getHeader("Content-Type")).thenReturn("image/jpeg");

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq(CLIENT_URL), captor.capture());
        assertEquals("image/jpeg", captor.getValue().contentType());
    }

    @Test
    public void shouldDetectMimeTypeFromMagicBytesWhenContentTypeHeaderAbsent() {
        CimdMetadataDocument dbDoc = cachedDocumentWithLogo(false, "https://localhost/logo.png");
        when(cimdMetadataDocumentService.findByDomainAndClientId("domain-id", CLIENT_URL))
                .thenReturn(Maybe.just(dbDoc));
        mockLogoFetch();
        when(response.getHeader("Content-Type")).thenReturn(null);

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq(CLIENT_URL), captor.capture());
        assertEquals("image/png", captor.getValue().contentType());
    }

    @Test
    public void shouldUseMetadataTtlForLogoMaxAge() {
        // Metadata Cache-Control resolves to 900s; logo server Cache-Control is irrelevant
        cimdSettings.setCacheTtlSeconds(1800);
        mockFetchSuccessWithCacheControl(metadataPayloadWithLogo(CLIENT_URL, "https://localhost/logo.png"), "max-age=900");

        cimdMetadataService.resolveClient(CLIENT_URL, templateClient()).test().assertComplete();

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq(CLIENT_URL), captor.capture());
        assertEquals(900L, captor.getValue().maxAgeSeconds());
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

    private void mockLogoFetch() {
        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.timeout(anyLong())).thenReturn(request);
        when(request.followRedirects(true)).thenReturn(request);
        when(request.as(any())).thenReturn((HttpRequest) request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));
        when(response.getHeader("Content-Type")).thenReturn("image/png");
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
                .put("jwks_uri", "https://localhost/jwks")
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
                .put("jwks_uri", "https://localhost/jwks")
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
