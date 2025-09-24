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

import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.Environment;

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
    private Environment originalEnvironment;

    @Before
    public void setUp() {
        // Store the original environment to restore it later
        originalEnvironment = StaticEnvironmentProvider.getEnvironment();
    }

    @After
    public void tearDown() {
        // Restore the original environment
        StaticEnvironmentProvider.setEnvironment(originalEnvironment);
    }

    /**
     * Helper method to set up a mock environment with specific properties
     */
    private void setupMockEnvironment(boolean includeDefaultPorts, boolean sanitizeParameters) {
        Environment mockEnv = mock(Environment.class);
        when(mockEnv.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_INCLUDE_DEFAULT_HOST_PORTS, boolean.class, false))
                .thenReturn(includeDefaultPorts);
        when(mockEnv.getProperty(StaticEnvironmentProvider.GATEWAY_ENDPOINT_SANITIZE_PARAMETERS_ENCODING, boolean.class, true))
                .thenReturn(sanitizeParameters);
        StaticEnvironmentProvider.setEnvironment(mockEnv);
    }

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
    public void shouldHandle_XForward_HostHeadersHasDefaultHttpPort_NoXForwardPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);

        assertEquals("http://myhost/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // default https port with http scheme should keep the forwarded port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("http://myhost:443/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForward_HostHeadersHasDefaultHttpsPort_NoXForwardPort() {
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);

        assertEquals("https://myhost/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // default http port with https scheme should keep the forwarded port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("https://myhost:80/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForward_HostHeadersHasDefaultPorts_WithLegacyMode() {
        // Set up legacy mode to include default ports
        setupMockEnvironment(true, true);

        // Test default HTTP port with legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("http://myhost:80/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // Test default HTTPS port with legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("https://myhost:443/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

    }

    @Test
    public void shouldHandle_XForward_HostHeadersHasPort_XForwardedPortTakesPrecedence() {
        // X-Forwarded-Host has port, but X-Forwarded-Port should take precedence
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8080");

        assertEquals("https://myhost:8080/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // Test with default port in X-Forwarded-Host but non-default in X-Forwarded-Port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("9090");
        assertEquals("http://myhost:9090/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForward_HostHeadersHasPort_XForwardPortIsDefault() {
        // X-Forwarded-Host has non-default port, but X-Forwarded-Port is default (should use X-Forwarded-Host port)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:8080");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");

        assertEquals("https://myhost:8080/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));

        // Test with HTTP
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:9090");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        assertEquals("http://myhost:9090/my/path?param1=value1", UriBuilderRequest.resolveProxyRequest(request, path, params, true));
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


    // ===== ORGANIZED SCENARIO 3 TESTS (Both Headers Provided) =====
    
    @Test
    public void shouldHandle_BothHeaders_XForwardedPortTakesPrecedence_NonDefaultPorts() {
        // Test that X-Forwarded-Port takes precedence over X-Forwarded-Host port and Host port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:8888");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("9999");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host:9999/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHeaders_XForwardedHostPortTakesPrecedence_DefaultPorts_NonLegacy() {
        // Test X-Forwarded-Host port takes precedence over Host port in non-legacy mode
        setupMockEnvironment(false, false); // Disable legacy mode
        
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("original-host:80");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("http://forwarded-host:443/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHeaders_XForwardedHostPortTakesPrecedence_DefaultPorts_Legacy() {
        // Test X-Forwarded-Host port takes precedence over Host port in legacy mode
        setupMockEnvironment(true, true); // Enable legacy mode
        
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("original-host:80");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("http://forwarded-host:443/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHeaders_NoHostPort_UsesXForwardedHostPort_NonLegacy() {
        // Test that X-Forwarded-Host port is used when Host header has no port (non-legacy mode omits default port)
        setupMockEnvironment(false, false); // Disable legacy mode
        
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host"); // No port in Host header

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_BothHeaders_NoHostPort_UsesXForwardedHostPort_Legacy() {
        // Test that X-Forwarded-Host port is used when Host header has no port (legacy mode includes default port)
        setupMockEnvironment(true, true); // Enable legacy mode
        
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("forwarded-host:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host"); // No port in Host header

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://forwarded-host:443/my/path?param1=value1", generatedUri);
    }

    @Test
    public void shouldHandle_LegacyMode_IncludeDefaultPorts() {
        // Test legacy mode where default ports should be included in URLs        
        setupMockEnvironment(true, false);
        
        // Test X-Forwarded-Host with default HTTPS port (443) - SHOULD be affected by legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("am.gateway.master.gravitee.dev:443");

        final var generatedUri = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        // X-Forwarded headers should include default ports when legacy mode is enabled
        assertEquals("https://am.gateway.master.gravitee.dev:443/my/path?param1=value1", generatedUri);

        // Test X-Forwarded-Port with default HTTPS port (443) - should NOT be affected by legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");

        final var generatedUri2 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        // X-Forwarded headers should always use new behavior (omit default ports) regardless of legacy mode
        assertEquals("https://myhost/my/path?param1=value1", generatedUri2);

        // Test Host header with default HTTPS port (443) - SHOULD be affected by legacy mode
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:443");

        final var generatedUri3 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        // Host header should use legacy behavior (include default ports) when legacy mode is enabled
        assertEquals("https://myhost:443/my/path?param1=value1", generatedUri3);
    }

    // ===== ORGANIZED SCENARIO 1 TESTS (Host Header Only) =====

    @Test
    public void shouldHandle_HostHeader_NoPort_AllSchemes() {
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        // HTTP scheme with no port
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost");
        assertEquals("http://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS scheme with no port
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost");
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_HostHeader_DefaultPorts_NonLegacyMode() {
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        // HTTP default port (80) - should be omitted
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost:80");
        assertEquals("http://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS default port (443) - should be omitted
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:443");
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_HostHeader_DefaultPorts_LegacyMode() {
        setupMockEnvironment(true, true); // Enable legacy mode
        
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        // HTTP default port (80) - should be included in legacy mode
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost:80");
        assertEquals("http://myhost:80/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS default port (443) - should be included in legacy mode
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:443");
        assertEquals("https://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_HostHeader_NonDefaultPorts_AllSchemes() {
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        // HTTP scheme with non-default port
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost:8080");
        assertEquals("http://myhost:8080/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS scheme with non-default port
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:8080");
        assertEquals("https://myhost:8080/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_HostHeader_MismatchedSchemeAndPort() {
        // Clear X-Forwarded headers to ensure Host header is used
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn(null);
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        // HTTP scheme with HTTPS default port (443) - should keep port
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("myhost:443");
        assertEquals("http://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS scheme with HTTP default port (80) - should keep port
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("myhost:80");
        assertEquals("https://myhost:80/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    // ===== ORGANIZED SCENARIO 2 TESTS (X-Forwarded Headers Only) =====

    @Test
    public void shouldHandle_XForwardedHeaders_NoPortInHost_AllSchemes() {
        // Clear Host header to ensure X-Forwarded headers are used
        when(request.host()).thenReturn(null);
        
        // HTTP Scheme - No port in X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        
        // No X-Forwarded-Port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("http://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Default HTTP port (80) - should be omitted
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        assertEquals("http://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default port (8080) - should be kept
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8080");
        assertEquals("http://myhost:8080/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS default port (443) with HTTP scheme - should be kept (mismatched scheme)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        assertEquals("http://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS Scheme - No port in X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        
        // No X-Forwarded-Port
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Default HTTPS port (443) - should be omitted
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default port (8888) - should be kept
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");
        assertEquals("https://myhost:8888/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTP default port (80) with HTTPS scheme - should be kept (mismatched scheme)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        assertEquals("https://myhost:80/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForwardedHeaders_DefaultPortInHost_AllSchemes() {
        // Clear Host header to ensure X-Forwarded headers are used
        when(request.host()).thenReturn(null);
        
        // HTTP Scheme - Default port (80) in X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        
        // No X-Forwarded-Port - default port should be omitted (legacy mode is false by default)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("http://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default X-Forwarded-Port - should take precedence
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8080");
        assertEquals("http://myhost:8080/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS Scheme - Default port (443) in X-Forwarded-Host
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        
        // No X-Forwarded-Port - default port should be omitted (legacy mode is false by default)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default X-Forwarded-Port - should take precedence
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");
        assertEquals("https://myhost:8888/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForwardedHeaders_MismatchedDefaultPortInHost_AllSchemes() {
        // Clear Host header to ensure X-Forwarded headers are used
        when(request.host()).thenReturn(null);
        
        // HTTP Scheme - HTTPS default port (443) in X-Forwarded-Host (mismatched)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        
        // No X-Forwarded-Port - mismatched port should be kept
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("http://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Default HTTP port (80) in X-Forwarded-Port - should use X-Forwarded-Host port instead
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        assertEquals("http://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default X-Forwarded-Port - should take precedence
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8080");
        assertEquals("http://myhost:8080/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // HTTPS Scheme - HTTP default port (80) in X-Forwarded-Host (mismatched)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        
        // No X-Forwarded-Port - mismatched port should be kept
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        assertEquals("https://myhost:80/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Default HTTPS port (443) in X-Forwarded-Port - should use X-Forwarded-Host port instead
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        assertEquals("https://myhost:80/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Non-default X-Forwarded-Port - should take precedence
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");
        assertEquals("https://myhost:8888/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForwardedHeaders_LegacyModeEffect() {
        // Test that legacy mode affects ports from X-Forwarded-Host but not X-Forwarded-Port
        setupMockEnvironment(true, true); // Enable legacy mode
        
        // Clear Host header to ensure X-Forwarded headers are used
        when(request.host()).thenReturn(null);
        
        // Test with default port in X-Forwarded-Host - should be INCLUDED in legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn(null);
        
        assertEquals("https://myhost:443/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Test with default port in X-Forwarded-Port - should be omitted regardless of legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        
        assertEquals("https://myhost/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
        
        // Test with non-default port - should be kept regardless of legacy mode
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("8888");
        
        assertEquals("https://myhost:8888/my/path?param1=value1", 
            UriBuilderRequest.resolveProxyRequest(request, path, params, true));
    }

    @Test
    public void shouldHandle_XForwardedHeaders_DefaultPortInXForwardedPort_NonDefaultPortInXForwardedHost() {
        // Test that when X-Forwarded-Port is default (80/443) but X-Forwarded-Host has non-default port,
        // the port from X-Forwarded-Host should be used instead of the default port from X-Forwarded-Port
        
        // Test case 1: HTTP scheme, X-Forwarded-Port=80, X-Forwarded-Host=myhost:8080
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:8080");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("original-host:8888");

        final var generatedUri1 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("http://myhost:8080/my/path?param1=value1", generatedUri1);

        // Test case 2: HTTPS scheme, X-Forwarded-Port=443, X-Forwarded-Host=myhost:8888
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:8888");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri2 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:8888/my/path?param1=value1", generatedUri2);
    }

    @Test
    public void shouldHandle_XForwardedHeaders_DefaultPortInXForwardedHost_LegacyMode() {
        // Test that in legacy mode, when both X-Forwarded-Port and X-Forwarded-Host have default ports,
        // the default port from X-Forwarded-Host should be included in the final URL (not ignored)
        setupMockEnvironment(true, true); // Enable legacy mode
        
        // Test case 1: HTTP scheme, X-Forwarded-Host=myhost:80 (default port), X-Forwarded-Port=80 (default port)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("http");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:80");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("80");
        when(request.scheme()).thenReturn("https");
        when(request.host()).thenReturn("original-host:8888");

        final var generatedUri1 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("http://myhost:80/my/path?param1=value1", generatedUri1);

        // Test case 2: HTTPS scheme, X-Forwarded-Host=myhost:443 (default port), X-Forwarded-Port=443 (default port)
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PROTO))).thenReturn("https");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_HOST))).thenReturn("myhost:443");
        when(request.getHeader(eq(HttpHeaders.X_FORWARDED_PORT))).thenReturn("443");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("original-host:8080");

        final var generatedUri2 = UriBuilderRequest.resolveProxyRequest(request, path, params, true);
        assertEquals("https://myhost:443/my/path?param1=value1", generatedUri2);
    }

}
