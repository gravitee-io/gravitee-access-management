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
package io.gravitee.am.gateway.handler.oidc.service.cimd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDException;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataCache;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataDocument;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CIMDMetadataFetcherImpl}.
 * Tests HTTP fetch flow, caching, SSRF validation, error handling, and audit reporting.
 */
@ExtendWith(MockitoExtension.class)
class CIMDMetadataFetcherImplTest {

    private CIMDMetadataFetcherImpl fetcher;

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> httpRequest;

    @Mock
    private HttpResponse<Buffer> httpResponse;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;

    private final String domainId = "test-domain";
    private final String clientIdUri = "https://agent.example.com/.well-known/oauth-client";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fetcher = new CIMDMetadataFetcherImpl();
        fetcher.setWebClient(webClient);
        fetcher.setObjectMapper(objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(fetcher, "auditService", auditService);
    }

    private io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataCache readCache() {
        return (io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataCache)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "cache");
    }

    private CIMDSettings createDefaultSettings() {
        CIMDSettings settings = new CIMDSettings();
        settings.setEnabled(true);
        settings.setFetchTimeoutMs(5000);
        settings.setMaxResponseSizeKb(10);
        settings.setCacheMaxEntries(100);
        settings.setCacheTtlSeconds(3600);
        settings.setAllowPrivateIpAddress(true);
        return settings;
    }

    // ========== Cache Tests ==========

    @Test
    void should_return_cached_document_on_cache_hit() {
        CIMDSettings settings = createDefaultSettings();
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);

        // Pre-populate cache
        CIMDMetadataDocument cachedDoc = new CIMDMetadataDocument();
        cachedDoc.setSoftwareId("cached-blueprint-123");
        cache.put(domainId, clientIdUri, cachedDoc);

        fetcher.setCache(cache);

        // Fetch should return cached document without HTTP call
        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("cached-blueprint-123", result.getSoftwareId());
        // Verify no HTTP call was made
        verify(webClient, never()).getAbs(anyString());
        // Verify audit reported cache hit
        verify(auditService).report(argThat(builder ->
                builder.getClass().getSimpleName().contains("CIMDAuditBuilder")));
    }

    @Test
    void should_fetch_and_cache_on_cache_miss() {
        CIMDSettings settings = createDefaultSettings();
        CIMDMetadataCache cache = new CIMDMetadataCache(100, 3600);
        fetcher.setCache(cache);

        // Setup HTTP mock
        String jsonBody = "{\"software_id\":\"fetched-blueprint-456\",\"client_name\":\"Test Agent\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        // Fetch should make HTTP call and cache result
        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("fetched-blueprint-456", result.getSoftwareId());
        assertEquals("Test Agent", result.getClientName());

        // Verify document was cached
        assertTrue(cache.get(domainId, clientIdUri).isPresent());
        assertEquals("fetched-blueprint-456", cache.get(domainId, clientIdUri).get().getSoftwareId());

        // Verify HTTP call was made
        verify(webClient).getAbs(clientIdUri);
        verify(httpRequest).timeout(5000L);
        verify(httpRequest).rxSend();

        // Verify audit reported cache miss
        verify(auditService).report(argThat(builder ->
                builder.getClass().getSimpleName().contains("CIMDAuditBuilder")));
    }

    @Test
    void should_bypass_cache_when_cache_is_null() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        // Setup HTTP mock
        String jsonBody = "{\"software_id\":\"no-cache-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        // Fetch should work without cache
        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("no-cache-123", result.getSoftwareId());
        verify(webClient).getAbs(clientIdUri);
    }

    // ========== initCache Tests ==========

    @Test
    void should_initialize_cache_when_enabled() {
        CIMDSettings settings = createDefaultSettings();
        settings.setEnabled(true);

        fetcher.initCache(settings);

        assertNotNull(readCache());
    }

    @Test
    void should_not_initialize_cache_when_disabled() {
        CIMDSettings settings = createDefaultSettings();
        settings.setEnabled(false);

        fetcher.initCache(settings);

        assertNull(readCache());
    }

    @Test
    void should_not_initialize_cache_when_settings_null() {
        fetcher.initCache(null);
        assertNull(readCache());
    }

    // ========== HTTP Status Tests ==========

    @Test
    void should_throw_exception_on_non_200_response() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(404);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("404"));
        assertTrue(exception.getMessage().contains(clientIdUri));
    }

    @Test
    void should_throw_exception_on_500_response() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(500);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("500"));
    }

    // ========== Content-Length Header Tests ==========

    @Test
    void should_reject_when_content_length_exceeds_max_size() {
        CIMDSettings settings = createDefaultSettings();
        settings.setMaxResponseSizeKb(10); // 10 KB max
        fetcher.setCache(null);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn("11264"); // 11 KB

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("Content-Length"));
        assertTrue(exception.getMessage().contains("exceeds maximum size"));
    }

    @Test
    void should_accept_content_length_within_max_size() {
        CIMDSettings settings = createDefaultSettings();
        settings.setMaxResponseSizeKb(10); // 10 KB max
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"ok-size-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));
        int bodySize = buffer.length();

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(String.valueOf(bodySize));
        when(httpResponse.body()).thenReturn(buffer);

        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("ok-size-123", result.getSoftwareId());
    }

    @Test
    void should_fall_through_on_malformed_content_length() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"malformed-header-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn("not-a-number");
        when(httpResponse.body()).thenReturn(buffer);

        // Should not throw; instead check body size
        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("malformed-header-123", result.getSoftwareId());
    }

    // ========== Body Size Tests ==========

    @Test
    void should_reject_when_body_exceeds_max_size() {
        CIMDSettings settings = createDefaultSettings();
        settings.setMaxResponseSizeKb(1); // 1 KB max
        fetcher.setCache(null);

        // Create buffer larger than 1 KB
        byte[] largeBody = new byte[1025];
        Buffer buffer = Buffer.buffer(largeBody);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("exceeds maximum size"));
    }

    @Test
    void should_accept_body_within_max_size() {
        CIMDSettings settings = createDefaultSettings();
        settings.setMaxResponseSizeKb(10);
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"within-size-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("within-size-123", result.getSoftwareId());
    }

    // ========== Empty Body Tests ==========

    @Test
    void should_throw_exception_on_null_body() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(null);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void should_throw_exception_on_zero_length_body() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        Buffer buffer = Buffer.buffer(new byte[0]);

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn("0");
        when(httpResponse.body()).thenReturn(buffer);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("empty"));
    }

    // ========== JSON Parsing Tests ==========

    @Test
    void should_throw_exception_on_malformed_json() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String malformedJson = "{invalid json}";
        Buffer buffer = Buffer.buffer(malformedJson.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("Failed to parse"));
        assertNotNull(exception.getCause());
    }

    @Test
    void should_parse_json_with_unknown_properties() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"unknown-props-123\",\"unknown_field\":\"ignored\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("unknown-props-123", result.getSoftwareId());
    }

    // ========== SSRF Validation Tests ==========

    @Test
    void should_reject_domain_not_in_allowed_domains_list() {
        CIMDSettings settings = createDefaultSettings();
        settings.setAllowedDomains(List.of("trusted.io"));
        fetcher.setCache(null);

        String badUri = "https://evil.io/.well-known/oauth-client";

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(badUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("not in the allowed domains") ||
                   exception.getMessage().contains("SSRF"));

        // Verify HTTP call was NOT made
        verify(webClient, never()).getAbs(anyString());

        // Verify rejection was audited
        verify(auditService).report(argThat(builder ->
                builder.getClass().getSimpleName().contains("CIMDAuditBuilder")));
    }

    @Test
    void should_accept_domain_in_allowed_domains_list() {
        CIMDSettings settings = createDefaultSettings();
        settings.setAllowedDomains(List.of("trusted.io"));

        String trustedUri = "https://trusted.io/.well-known/oauth-client";

        String jsonBody = "{\"software_id\":\"trusted-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(trustedUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDMetadataDocument result = fetcher.fetch(trustedUri, domainId, settings)
                .blockingGet();

        assertEquals("trusted-123", result.getSoftwareId());
        verify(webClient).getAbs(trustedUri);
    }

    @Test
    void should_reject_invalid_uri_syntax() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String invalidUri = "not a valid uri:::";

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(invalidUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("Invalid"));
        verify(webClient, never()).getAbs(anyString());
    }

    // ========== Timeout Tests ==========

    @Test
    void should_use_configured_fetch_timeout() {
        CIMDSettings settings = createDefaultSettings();
        settings.setFetchTimeoutMs(8000);
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"timeout-test-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(8000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        fetcher.fetch(clientIdUri, domainId, settings).blockingGet();

        verify(httpRequest).timeout(8000L);
    }

    // ========== Complex JSON Tests ==========

    @Test
    void should_parse_complex_metadata_document() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String complexJson = "{\"software_id\":\"agent-123\"," +
                "\"client_name\":\"My Agent\"," +
                "\"application_type\":\"web\"," +
                "\"grant_types\":[\"authorization_code\",\"refresh_token\"]," +
                "\"response_types\":[\"code\",\"id_token\"]," +
                "\"redirect_uris\":[\"https://agent.example.com/callback\"]," +
                "\"contacts\":[\"admin@example.com\"]}";

        Buffer buffer = Buffer.buffer(complexJson.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        CIMDMetadataDocument result = fetcher.fetch(clientIdUri, domainId, settings)
                .blockingGet();

        assertEquals("agent-123", result.getSoftwareId());
        assertEquals("My Agent", result.getClientName());
        assertEquals("web", result.getApplicationType());
        assertNotNull(result.getGrantTypes());
        assertEquals(2, result.getGrantTypes().size());
        assertTrue(result.getGrantTypes().contains("authorization_code"));
    }

    // ========== Error Wrapping Tests ==========

    @Test
    void should_wrap_http_exception_in_cimd_exception() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        RuntimeException httpError = new RuntimeException("Connection timeout");

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.error(httpError));

        CIMDException exception = assertThrows(CIMDException.class, () ->
                fetcher.fetch(clientIdUri, domainId, settings).blockingGet());

        assertTrue(exception.getMessage().contains("CIMD metadata fetch failed"));
        assertEquals(httpError, exception.getCause());
    }

    // ========== Audit Tests ==========

    @Test
    void should_report_fetch_audit_with_duration() {
        CIMDSettings settings = createDefaultSettings();
        fetcher.setCache(null);

        String jsonBody = "{\"software_id\":\"audit-test-123\"}";
        Buffer buffer = Buffer.buffer(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(webClient.getAbs(clientIdUri)).thenReturn(httpRequest);
        when(httpRequest.timeout(5000L)).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.getHeader("Content-Length")).thenReturn(null);
        when(httpResponse.body()).thenReturn(buffer);

        fetcher.fetch(clientIdUri, domainId, settings).blockingGet();

        verify(auditService).report(any());
    }

    @Test
    void should_report_ssrf_rejection_audit() {
        CIMDSettings settings = createDefaultSettings();
        settings.setAllowedDomains(List.of("trusted.io"));
        fetcher.setCache(null);

        String rejectedUri = "https://evil.io/.well-known/oauth-client";

        assertThrows(CIMDException.class, () ->
                fetcher.fetch(rejectedUri, domainId, settings).blockingGet());

        verify(auditService).report(any());
    }
}
