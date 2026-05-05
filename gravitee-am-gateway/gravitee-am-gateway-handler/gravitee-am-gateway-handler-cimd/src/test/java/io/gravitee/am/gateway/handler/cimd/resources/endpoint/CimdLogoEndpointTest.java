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
package io.gravitee.am.gateway.handler.cimd.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.cimd.CachedLogo;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdLogoCacheService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.ClientIds;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdLogoEndpointTest {

    private static final String RAW_CLIENT_ID = "https://Example.COM/registry/meta";

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest request;

    @Mock
    private HttpServerResponse response;

    @Mock
    private Domain domain;

    @Mock
    private CimdMetadataDocumentManager documentManager;

    @Mock
    private CimdLogoCacheService logoCacheService;

    private CimdLogoEndpoint endpoint;
    private CIMDSettings cimdSettings;
    private OIDCSettings oidcSettings;

    @Before
    public void setUp() {
        cimdSettings = new CIMDSettings();
        cimdSettings.setEnabled(true);
        cimdSettings.setAllowPrivateIpAddress(true);
        oidcSettings = new OIDCSettings();
        oidcSettings.setCimdSettings(cimdSettings);

        when(domain.getOidc()).thenReturn(oidcSettings);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);

        endpoint = new CimdLogoEndpoint(domain, documentManager, logoCacheService);
    }

    @Test
    public void shouldReturn400WhenClientIdMissing() {
        when(request.getParam("clientId")).thenReturn(" ");

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(response).end();
    }

    @Test
    public void shouldServeLogoFromCache() {
        final String canonical = ClientIds.canonicalize(RAW_CLIENT_ID);
        when(request.getParam("clientId")).thenReturn(RAW_CLIENT_ID);
        CachedLogo cached = new CachedLogo(new byte[]{9, 9, 9}, "image/svg+xml", 42L);
        when(documentManager.getLogoByClientId(canonical)).thenReturn(Optional.of(cached));

        endpoint.handle(routingContext);

        verify(logoCacheService, never()).fetchAndCacheNow(anyString(), anyString(), anyLong(), any(CIMDSettings.class));
        verify(response).setStatusCode(200);
        verify(response).putHeader(eq("Content-Type"), eq("image/svg+xml"));
        verify(response).putHeader(eq("Cache-Control"), eq("max-age=42"));
        verify(response).end(any(Buffer.class));
    }

    @Test
    public void shouldFetchAndServeLogoOnCacheMissWhenMetadataContainsLogoUri() {
        final String canonical = ClientIds.canonicalize(RAW_CLIENT_ID);
        when(request.getParam("clientId")).thenReturn(RAW_CLIENT_ID);
        when(documentManager.getLogoByClientId(canonical)).thenReturn(Optional.empty());

        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setExpiresAt(new Date(System.currentTimeMillis() + 120_000));
        doc.setMetadata(new JsonObject().put("logo_uri", "https://example.com/logo.png").encode());
        when(documentManager.resolve(canonical)).thenReturn(Single.just(Optional.of(doc)));

        CachedLogo fetched = new CachedLogo(new byte[]{1, 2, 3}, "image/png", 90L);
        when(logoCacheService.fetchAndCacheNow(eq(canonical), eq("https://example.com/logo.png"), anyLong(), eq(cimdSettings)))
                .thenReturn(Single.just(Optional.of(fetched)));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(200);
        verify(response).putHeader(eq("Content-Type"), eq("image/png"));
        verify(response).putHeader(eq("Cache-Control"), eq("max-age=90"));
        verify(response).end(any(Buffer.class));
    }

    @Test
    public void shouldReturn404WhenCimdDisabled() {
        cimdSettings.setEnabled(false);

        final String canonical = ClientIds.canonicalize(RAW_CLIENT_ID);
        when(request.getParam("clientId")).thenReturn(RAW_CLIENT_ID);
        when(documentManager.getLogoByClientId(canonical)).thenReturn(Optional.empty());

        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setExpiresAt(new Date(System.currentTimeMillis() + 120_000));
        doc.setMetadata(new JsonObject().put("logo_uri", "https://example.com/logo.png").encode());
        when(documentManager.resolve(canonical)).thenReturn(Single.just(Optional.of(doc)));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(404);
        verify(response).end();
    }

    @Test
    public void shouldReturn404WhenClientMetadataNotFound() {
        final String canonical = ClientIds.canonicalize(RAW_CLIENT_ID);
        when(request.getParam("clientId")).thenReturn(RAW_CLIENT_ID);
        when(documentManager.getLogoByClientId(canonical)).thenReturn(Optional.empty());
        when(documentManager.resolve(canonical)).thenReturn(Single.just(Optional.empty()));

        endpoint.handle(routingContext);

        verify(logoCacheService, never()).fetchAndCacheNow(anyString(), anyString(), anyLong(), any(CIMDSettings.class));
        verify(response).setStatusCode(404);
        verify(response).end();
    }
}
