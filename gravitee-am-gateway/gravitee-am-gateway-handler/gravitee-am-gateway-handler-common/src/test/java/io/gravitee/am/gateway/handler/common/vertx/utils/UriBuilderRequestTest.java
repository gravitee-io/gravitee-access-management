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

import io.gravitee.common.http.HttpHeaders;
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
    public void shouldHandle_XForward_Headers_DefaultHttpPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");

        assertEquals("http://myhost/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // default https port with http scheme should keep the forwarded port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        assertEquals("http://myhost:443/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }


    @Test
    public void shouldHandle_XForward_Headers_DefaultHttpsPort() {
        // Test X-Forwarded-Port with default HTTPS port (443)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");

        assertEquals("https://myhost/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // Test X-Forwarded-Host with default HTTPS port (443) - original bug report case
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("am.gateway.master.gravitee.dev:443");

        assertEquals("https://am.gateway.master.gravitee.dev/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // Test default HTTP port with HTTPS scheme should keep the forwarded port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        assertEquals("https://myhost:80/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
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

    @Test
    public void shouldHandle_Host_Headers_NonDefaultPort() {
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:8080/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_Host_Headers_DefaultHttpPort() {
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost:80");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("http://myhost/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_Host_Headers_DefaultHttpsPort() {
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:443");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_Host_Headers_MismatchedProtocolAndPort() {
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:80");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:80/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHostAndXForwardedHeaders_XForwardedTakesPrecedence() {
        // Test that X-Forwarded headers take precedence over Host header
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:8888");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("9999");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host:9999/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_XForwardedPrefix() {
        // Test X-Forwarded-Prefix with trailing slash
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("example.com");
        when(request.getHeader(eq("X-Forwarded-Prefix"))).thenReturn("/api/");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://example.com/api/my/path?param1=value1", generatedUri);

        // Test X-Forwarded-Prefix without trailing slash
        when(request.getHeader(eq("X-Forwarded-Prefix"))).thenReturn("/api");

        final var generatedUri2 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://example.com/api/my/path?param1=value1", generatedUri2);
    }

    @Test
    public void shouldHandle_BothHostAndXForwardedHeaders_XForwardedHostWithDefaultPort() {
        // Test X-Forwarded-Host with default port should not include port in result
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:443");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHostAndXForwardedHeaders_XForwardedPortOverridesHostPort() {
        // Test that X-Forwarded-Port overrides port from X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:7777");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("6666");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host:6666/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHostAndXForwardedHeaders_XForwardedPortDefaultOverridesHostPort() {
        // Test that X-Forwarded-Port with default value overrides non-default port from X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:7777");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host/my/path?param1=value1", generatedUri);
    }
}
