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

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class WebClientBuilderTest {

    @Mock
    private Environment environment;

    @InjectMocks
    private WebClientBuilder webClientBuilder = new WebClientBuilder();
    private Vertx vertx;

    @BeforeEach
    void setup() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        vertx = mock(Vertx.class);
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
}
