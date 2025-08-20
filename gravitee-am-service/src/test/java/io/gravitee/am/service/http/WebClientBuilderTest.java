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
package io.gravitee.am.service.http;

import io.gravitee.common.util.EnvironmentUtils;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class WebClientBuilderTest {

    @InjectMocks
    private WebClientBuilder webClientBuilder;

    private Environment environment;
    private Vertx vertx;

    @BeforeEach
    void setup() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        vertx = mock(Vertx.class);
        environment = mock(Environment.class);
        webClientBuilder = new WebClientBuilder(environment);
        when(vertx.getDelegate()).thenReturn(vertDelegate);
        lenient().when(environment.getProperty(anyString(), eq(Boolean.class), anyBoolean())).thenReturn(false);
        lenient().when(environment.getProperty("httpClient.ssl.truststore.type")).thenReturn(null);
    }

    @Test
    void shouldApplySSLOptions_trustAll() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(true);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertTrue(webClientOptions.isTrustAll());
    }

    @Test
    void shouldApplySSLOptions_invalidTrustStoreType() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.truststore.type")).thenReturn("unknown");

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNull(webClientOptions.getTrustOptions());
        assertNull(webClientOptions.getPfxTrustOptions());
        assertNull(webClientOptions.getPemTrustOptions());
    }

    @Test
    void shouldApplySSLOptions_jksTrustStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.truststore.type")).thenReturn("jks");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.jks");
        when(environment.getProperty("httpClient.ssl.truststore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNotNull(webClientOptions.getTrustOptions());
        assertNull(webClientOptions.getPfxTrustOptions());
        assertNull(webClientOptions.getPemTrustOptions());
    }

    @Test
    void shouldApplySSLOptions_pfxTrustStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.truststore.type")).thenReturn("pkcs12");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.p12");
        when(environment.getProperty("httpClient.ssl.truststore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNotNull(webClientOptions.getTrustOptions());
        assertNotNull(webClientOptions.getPfxTrustOptions());
        assertNull(webClientOptions.getPemTrustOptions());
    }

    @Test
    void shouldApplySSLOptions_pemTrustStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.truststore.type")).thenReturn("pem");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.pem");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNotNull(webClientOptions.getTrustOptions());
        assertNull(webClientOptions.getPfxTrustOptions());
        assertNotNull(webClientOptions.getPemTrustOptions());
    }

    @Test
    void shouldApplySSLOptions_invalidKeyStoreType() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.keystore.type")).thenReturn("unknown");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNull(webClientOptions.getTrustOptions());
        assertNull(webClientOptions.getPfxTrustOptions());
        assertNull(webClientOptions.getPemTrustOptions());
    }

    @Test
    void shouldApplySSLOptions_jksKeyStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.keystore.type")).thenReturn("jks");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/keystore.jks");
        when(environment.getProperty("httpClient.ssl.keystore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNotNull(webClientOptions.getKeyStoreOptions());
        assertNull(webClientOptions.getPfxKeyCertOptions());
        assertNull(webClientOptions.getPemKeyCertOptions());
    }

    @Test
    void shouldApplySSLOptions_pfxKeyStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.keystore.type")).thenReturn("pkcs12");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/keystore.p12");
        when(environment.getProperty("httpClient.ssl.keystore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNull(webClientOptions.getKeyStoreOptions());
        assertNotNull(webClientOptions.getPfxKeyCertOptions());
        assertNull(webClientOptions.getPemKeyCertOptions());
    }

    @Test
    void shouldApplySSLOptions_pemKeyStore() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("httpClient.ssl.keystore.type")).thenReturn("pem");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/certificate.key");
        when(environment.getProperty("httpClient.ssl.keystore.keyPath")).thenReturn("/private.key");
        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isTrustAll());
        assertNull(webClientOptions.getKeyStoreOptions());
        assertNull(webClientOptions.getPfxKeyCertOptions());
        assertNotNull(webClientOptions.getPemKeyCertOptions());
    }

    @Test
    void shouldApplyProxy() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.proxy.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.proxy.type", "HTTP")).thenReturn("HTTP");
        when(environment.getProperty("httpClient.proxy.http.host", "localhost")).thenReturn("localhost");
        when(environment.getProperty("httpClient.proxy.http.port", Integer.class, Integer.valueOf(System.getProperty("http.proxyPort", "3128")))).thenReturn(8080);
        when(environment.getProperty("httpClient.proxy.http.username")).thenReturn("user");
        when(environment.getProperty("httpClient.proxy.http.password")).thenReturn("password");


        webClientBuilder.createWebClient(vertx, webClientOptions, null, true);

        assertTrue(webClientOptions.isTrustAll());
        assertEquals("localhost", webClientOptions.getProxyOptions().getHost());
        assertEquals(8080, webClientOptions.getProxyOptions().getPort());
    }

    @Test
    void shouldNotApplyProxyWhenExcludedHosts() {
        WebClientOptions webClientOptions = new WebClientOptions();

        Map<String, Object> mockMap = new HashMap<>();
        mockMap.put("httpClient.proxy.exclude-hosts[0]", "*.test.com");
        mockMap.put("httpClient.proxy.exclude-hosts[1]", "abc.test.com");

        try (MockedStatic<EnvironmentUtils> utilities = mockStatic(EnvironmentUtils.class)) {
            utilities.when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq("httpClient.proxy.exclude-hosts")))
                    .thenReturn(mockMap);

            when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
            when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(true);

            webClientBuilder.createWebClient(vertx, webClientOptions, "http://test.com", true);

            assertTrue(webClientOptions.isTrustAll());
            assertNull(webClientOptions.getProxyOptions());
        }
    }

    @Test
    void shouldNotApplyProxy() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(true);

        webClientBuilder.createWebClient(vertx, webClientOptions, null, false);

        assertTrue(webClientOptions.isTrustAll());
        assertNull(webClientOptions.getProxyOptions());
    }

    @Test
    void shouldEnableHttp2WithDefaultSettings() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, 65535)).thenReturn(65535);
        when(environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, 60)).thenReturn(60);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertTrue(webClientOptions.isUseAlpn());
        assertEquals(65535, webClientOptions.getHttp2ConnectionWindowSize());
        assertEquals(60, webClientOptions.getHttp2KeepAliveTimeout());
    }

    @Test
    void shouldDisableHttp2WhenExplicitlyDisabled() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(false);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertFalse(webClientOptions.isUseAlpn());
    }

    @Test
    void shouldApplyMaxPoolSizeToHttp2() {
        WebClientOptions webClientOptions = new WebClientOptions().setMaxPoolSize(50);
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, 65535)).thenReturn(65535);
        when(environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, 60)).thenReturn(60);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertTrue(webClientOptions.isUseAlpn());
        assertEquals(50, webClientOptions.getHttp2MaxPoolSize());
    }

    @Test
    void shouldApplyCustomHttp2ConnectionWindowSize() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, 65535)).thenReturn(131072);
        when(environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, 60)).thenReturn(60);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertTrue(webClientOptions.isUseAlpn());
        assertEquals(131072, webClientOptions.getHttp2ConnectionWindowSize());
    }

    @Test
    void shouldApplyCustomHttp2KeepAliveTimeout() {
        WebClientOptions webClientOptions = new WebClientOptions();
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, 65535)).thenReturn(65535);
        when(environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, 60)).thenReturn(200);

        webClientBuilder.createWebClient(vertx, webClientOptions);

        assertTrue(webClientOptions.isUseAlpn());
        assertEquals(200, webClientOptions.getHttp2KeepAliveTimeout());
    }

    @Test
    void shouldConfigureHttp2ForUrlBasedWebClient() throws Exception {
        when(environment.getProperty("httpClient.timeout", Integer.class, 10000)).thenReturn(10000);
        when(environment.getProperty("httpClient.http2.enabled", Boolean.class, true)).thenReturn(true);
        when(environment.getProperty("httpClient.http2.connectionWindowSize", Integer.class, 65535)).thenReturn(65535);
        when(environment.getProperty("httpClient.http2.keepAliveTimeout", Integer.class, 60)).thenReturn(60);

        var webClient = webClientBuilder.createWebClient(vertx, new java.net.URL("https://example.com"));

        assertNotNull(webClient);
    }
}
