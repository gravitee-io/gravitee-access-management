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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebClientBuilderTest {

    @Mock
    private Environment environment;

    @InjectMocks
    private WebClientBuilder webClientBuilder = new WebClientBuilder();

    @Test
    public void shouldApplySSLOptions_trustAll() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", true);
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(true);
    }

    @Test
    public void shouldApplySSLOptions_invalidTrustStoreType() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslTrustStoreType", "unknown");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, never()).setTrustStoreOptions(any());
        verify(webClientOptions, never()).setPfxTrustOptions(any());
        verify(webClientOptions, never()).setPemTrustOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_jksTrustStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslTrustStoreType", "jks");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.jks");
        when(environment.getProperty("httpClient.ssl.truststore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, times(1)).setTrustStoreOptions(any());
        verify(webClientOptions, never()).setKeyStoreOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_pfxTrustStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslTrustStoreType", "pkcs12");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.p12");
        when(environment.getProperty("httpClient.ssl.truststore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, times(1)).setPfxTrustOptions(any());
        verify(webClientOptions, never()).setPfxKeyCertOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_pemTrustStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslTrustStoreType", "pem");
        when(environment.getProperty("httpClient.ssl.truststore.path")).thenReturn("/truststore.pem");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, times(1)).setPemTrustOptions(any());
        verify(webClientOptions, never()).setPemKeyCertOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_invalidKeyStoreType() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslKeyStoreType", "unknown");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, never()).setKeyStoreOptions(any());
        verify(webClientOptions, never()).setPfxKeyCertOptions(any());
        verify(webClientOptions, never()).setPemKeyCertOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_jksKeyStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslKeyStoreType", "jks");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/keystore.jks");
        when(environment.getProperty("httpClient.ssl.keystore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, never()).setTrustStoreOptions(any());
        verify(webClientOptions, times(1)).setKeyStoreOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_pfxKeyStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslKeyStoreType", "pkcs12");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/keystore.p12");
        when(environment.getProperty("httpClient.ssl.keystore.password")).thenReturn("password");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, never()).setPfxTrustOptions(any());
        verify(webClientOptions, times(1)).setPfxKeyCertOptions(any());
    }

    @Test
    public void shouldApplySSLOptions_pemKeyStore() {
        io.vertx.core.Vertx vertDelegate = mock(io.vertx.core.Vertx.class);
        Vertx vertx = mock(Vertx.class);
        when(vertx.getDelegate()).thenReturn(vertDelegate);

        WebClientOptions webClientOptions = mock(WebClientOptions.class);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLEnabled", true);
        ReflectionTestUtils.setField(webClientBuilder, "isSSLTrustAllEnabled", false);
        ReflectionTestUtils.setField(webClientBuilder, "sslKeyStoreType", "pem");
        when(environment.getProperty("httpClient.ssl.keystore.path")).thenReturn("/certificate.key");
        when(environment.getProperty("httpClient.ssl.keystore.keyPath")).thenReturn("/private.key");
        webClientBuilder.createWebClient(vertx, webClientOptions);
        verify(webClientOptions, times(1)).setTrustAll(false);
        verify(webClientOptions, never()).setPemTrustOptions(any());
        verify(webClientOptions, times(1)).setPemKeyCertOptions(any());
    }
}
