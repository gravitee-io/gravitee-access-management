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
package io.gravitee.am.gateway.handler.common.client.cimd;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.buffer.Buffer;
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

import java.net.URI;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdLogoCacheServiceTest {

    @BeforeClass
    public static void setupSchedulers() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
    }

    @AfterClass
    public static void resetSchedulers() {
        RxJavaPlugins.reset();
    }

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> request;

    @Mock
    private HttpResponse<Buffer> response;

    @Mock
    private CimdMetadataDocumentManager cimdMetadataDocumentManager;

    @Mock
    private CimdUriTrustValidator cimdUriTrustValidator;

    private CIMDSettings settings;
    private CimdLogoCacheService cacheService;

    @Before
    public void setUp() throws Exception {
        settings = new CIMDSettings();
        settings.setAllowPrivateIpAddress(true);
        settings.setFetchTimeoutMs(1500);

        lenient()
                .when(cimdUriTrustValidator.parseHttpUrl(anyString(), eq("logo_uri")))
                .thenAnswer(inv -> UriBuilder.fromHttpUrl(inv.getArgument(0)).build());
        lenient().doNothing().when(cimdUriTrustValidator).validateTrust(any(), any(), eq("logo_uri"));
        lenient()
                .when(cimdUriTrustValidator.validateResolvableHost(anyString(), eq("logo_uri"), any()))
                .thenReturn(Completable.complete());

        cacheService = new CimdLogoCacheService(webClient, cimdMetadataDocumentManager, cimdUriTrustValidator);
    }

    @Test
    public void fetchAndCacheNowReturnsEmptyWhenLogoUriBlank() {
        cacheService.fetchAndCacheNow("cid", "", 60L, settings).test().assertResult(Optional.empty());
        verifyNoInteractions(webClient);
    }

    @Test
    public void fetchAndCacheNowReturnsExistingCachedLogoWithoutHttp() {
        CachedLogo existing = new CachedLogo(new byte[]{1}, "image/png", 120L);
        when(cimdMetadataDocumentManager.getLogoByClientId("cid")).thenReturn(Optional.of(existing));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.of(existing));

        verifyNoInteractions(webClient);
    }

    @Test
    public void fetchAndCacheNowReturnsEmptyWhenParseFails() {
        when(cimdUriTrustValidator.parseHttpUrl(anyString(), eq("logo_uri")))
                .thenThrow(new InvalidClientMetadataException("bad"));

        cacheService.fetchAndCacheNow("cid", "not-a-url", 60L, settings).test().assertResult(Optional.empty());

        verifyNoInteractions(webClient);
    }

    @Test
    public void fetchAndCacheNowReturnsEmptyWhenTrustRejected() {
        doThrow(new InvalidClientMetadataException("untrusted"))
                .when(cimdUriTrustValidator)
                .validateTrust(any(URI.class), any(CIMDSettings.class), eq("logo_uri"));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.empty());

        verifyNoInteractions(webClient);
    }

    @Test
    public void fetchAndCacheNowReturnsEmptyWhenResolvableHostRejected() {
        when(cimdUriTrustValidator.validateResolvableHost(anyString(), eq("logo_uri"), any()))
                .thenReturn(Completable.error(new InvalidClientMetadataException("private")));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.empty());

        verifyNoInteractions(webClient);
    }

    @Test
    public void prefetchLogoAsyncDoesNotPutLogoWhenTrustRejected() {
        doThrow(new InvalidClientMetadataException("untrusted"))
                .when(cimdUriTrustValidator)
                .validateTrust(any(URI.class), any(CIMDSettings.class), eq("logo_uri"));

        cacheService.prefetchLogoAsync("cid", "http://bad/logo.png", 60L, settings);

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void prefetchLogoAsyncDoesNotPutLogoWhenResolvableHostRejected() {
        when(cimdUriTrustValidator.validateResolvableHost(anyString(), eq("logo_uri"), any()))
                .thenReturn(Completable.error(new InvalidClientMetadataException("blocked")));

        cacheService.prefetchLogoAsync("cid", "https://branding.vendor.example/logo.png", 60L, settings);

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    public void fetchAndCacheNowUsesFollowRedirectsTrue() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertValue(Optional::isPresent);

        verify(request).followRedirects(true);
    }

    @Test
    public void fetchAndCacheNowDoesNotPutLogoWhenResponseNotOk() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(404);

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.empty());

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void fetchAndCacheNowDoesNotPutLogoWhenBodyEmpty() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer());

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.empty());

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void fetchAndCacheNowDoesNotPutLogoWhenBodyExceedsMaxSize() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[(int) CimdLogoCacheService.MAX_LOGO_SIZE_BYTES + 1]));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertResult(Optional.empty());

        verify(cimdMetadataDocumentManager, never()).putLogo(anyString(), any());
    }

    @Test
    public void fetchAndCacheNowUsesContentTypeHeaderWhenPresent() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[]{1, 2, 3}));
        when(response.getHeader("Content-Type")).thenReturn("image/jpeg");

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertValue(Optional::isPresent);

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq("cid"), captor.capture());
        assertEquals("image/jpeg", captor.getValue().contentType());
    }

    @Test
    public void fetchAndCacheNowDetectsMimeFromMagicBytesWhenContentTypeAbsent() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));
        when(response.getHeader("Content-Type")).thenReturn(null);

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 60L, settings).test().assertValue(Optional::isPresent);

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq("cid"), captor.capture());
        assertEquals("image/png", captor.getValue().contentType());
    }

    @Test
    public void fetchAndCacheNowPassesMetadataTtlAsLogoMaxAge() {
        mockLogoPipeline();
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsBuffer()).thenReturn(Buffer.buffer(new byte[]{1, 2, 3}));

        cacheService.fetchAndCacheNow("cid", "https://localhost/logo.png", 900L, settings).test().assertComplete();

        ArgumentCaptor<CachedLogo> captor = ArgumentCaptor.forClass(CachedLogo.class);
        verify(cimdMetadataDocumentManager).putLogo(eq("cid"), captor.capture());
        assertEquals(900L, captor.getValue().maxAgeSeconds());
    }

    private void mockLogoPipeline() {
        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.timeout(anyLong())).thenReturn(request);
        when(request.followRedirects(true)).thenReturn(request);
        when(request.as(any())).thenReturn((HttpRequest) request);
        when(request.rxSend()).thenReturn(Single.just(response));
    }
}
