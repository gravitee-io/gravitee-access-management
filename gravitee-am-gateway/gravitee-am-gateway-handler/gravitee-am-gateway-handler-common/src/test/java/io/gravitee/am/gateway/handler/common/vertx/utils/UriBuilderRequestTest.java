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
package io.gravitee.am.gateway.handler.common.vertx.utils;

import com.google.common.net.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UriBuilderRequestTest {
    private final MultiMap params = MultiMap.caseInsensitiveMultiMap().add("param1", "value1");
    private final String path = "/my/path";
    private final HttpServerRequest request = mock(HttpServerRequest.class);

    @Test
    public void shouldHandle_XForward_Headers() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:8888/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_XForward_Headers_HostWithPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:9999");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:8888/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_XForward_Headers_HostWithPort_NoXForwardPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:9999");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:9999/my/path?param1=value1", generatedUri);
    }
    @Test
    public void shouldHandle_XForward_Headers_HostWithoutPort_NoXForwardPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost/my/path?param1=value1", generatedUri);
    }
}
