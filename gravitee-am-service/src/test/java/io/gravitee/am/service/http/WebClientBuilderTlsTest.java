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

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Exercises a real TLS handshake between the {@link WebClientBuilder} client and a Vert.x HTTPS server
 * presenting a self-signed certificate, with both the JDK and the OpenSSL engines. It guards against Netty/Vert.x
 * upgrades (for instance through the gravitee-bom) that would break the TLS stack, which mock-based tests cannot detect.
 * <p>
 * Netty 4.2.18+ refused a trust-all trust manager (a plain X509TrustManager) with an OpenSSL client context
 * ("Endpoint identification algorithm 'HTTPS' is configured but the trust manager does not support extended trust
 * manager verification"), so any HTTPS call with {@code httpClient.ssl.trustAll} failed before connecting.
 */
class WebClientBuilderTlsTest {

    private Vertx vertx;
    private HttpServer server;
    private Environment environment;
    private WebClientBuilder webClientBuilder;

    @BeforeEach
    void setup() throws Exception {
        vertx = Vertx.vertx();

        SelfSignedCertificate certificate = SelfSignedCertificate.create("localhost");
        HttpServerOptions serverOptions = new HttpServerOptions()
                .setSsl(true)
                .setHost("localhost")
                .setPort(0)
                .setKeyCertOptions(certificate.keyCertOptions());
        server = vertx.getDelegate()
                .createHttpServer(serverOptions)
                .requestHandler(req -> req.response().end("{\"status\": \"green\"}"))
                .listen()
                .toCompletionStage()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        environment = mock(Environment.class);
        lenient().when(environment.getProperty(anyString(), eq(Boolean.class), anyBoolean())).thenReturn(false);
        lenient().when(environment.getProperty(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> i.getArgument(2));
        webClientBuilder = new WebClientBuilder(environment);
    }

    private static WebClientOptions clientOptions(boolean openSsl) {
        if (openSsl) {
            Assumptions.assumeTrue(OpenSSLEngineOptions.isAvailable(), "OpenSSL (netty-tcnative) is not available on this platform");
        }
        SSLEngineOptions engine = openSsl ? new OpenSSLEngineOptions() : new JdkSSLEngineOptions();
        return new WebClientOptions().setSsl(true).setSslEngineOptions(engine);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().blockingAwait(10, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCallHttpsEndpointWithTrustAll(boolean openSsl) {
        lenient().when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);
        lenient().when(environment.getProperty("httpClient.ssl.trustAll", Boolean.class, false)).thenReturn(true);
        lenient().when(environment.getProperty("httpClient.ssl.verifyHost", Boolean.class, true)).thenReturn(false);

        WebClient client = webClientBuilder.createWebClient(vertx, clientOptions(openSsl), null, false);

        var response = client.get(server.actualPort(), "localhost", "/")
                .rxSend()
                .blockingGet();

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\": \"green\"}", response.bodyAsString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectSelfSignedCertificateWithoutTrustAll(boolean openSsl) {
        lenient().when(environment.getProperty("httpClient.ssl.enabled", Boolean.class, false)).thenReturn(true);

        WebClient client = webClientBuilder.createWebClient(vertx, clientOptions(openSsl), null, false);

        assertThrows(Exception.class, () -> client.get(server.actualPort(), "localhost", "/")
                .rxSend()
                .blockingGet());
    }
}
