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
package io.gravitee.am.service.jwk.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * Transport hardening of the shared JWKS client, exercised against a real HTTP server: a redirect
 * must not be followed, so an SSRF guard that vetted the URL cannot be walked past, and a server
 * that never answers must not hold the fetch open indefinitely.
 */
public class WebClientJWKSetFetcherHardeningTest {

    private static final String JWKS =
            "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"KID\",\"n\":\"modulus\",\"e\":\"exponent\"}]}";

    private static final long TIMEOUT_MS = 300L;

    private HttpServer server;
    private int port;
    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    private Vertx vertx;
    private WebClient webClient;
    private WebClientJWKSetFetcher fetcher;

    @Before
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            HttpHandler h = handler.get();
            if (h != null) {
                h.handle(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();

        vertx = Vertx.vertx();
        webClient = WebClient.create(vertx);
        fetcher = new WebClientJWKSetFetcher(webClient, TIMEOUT_MS);
    }

    @After
    public void stop() {
        server.stop(0);
        if (webClient != null) {
            webClient.close();
        }
        if (vertx != null) {
            vertx.close().blockingAwait();
        }
    }

    @Test
    public void shouldNotFollowARedirect() {
        redirectOnceThenServeKeys();

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertError(InvalidClientMetadataException.class);
        assertEquals(1, requests.get());
    }

    @Test
    public void shouldNotFollowARedirectOnTheBoundedPath() {
        redirectOnceThenServeKeys();

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url(), 1024L).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertError(InvalidClientMetadataException.class);
        assertEquals(1, requests.get());
    }

    @Test
    public void shouldGiveUpWhenTheServerDoesNotAnswerWithinTheTimeout() {
        handler.set(exchange -> {
            try {
                Thread.sleep(TIMEOUT_MS * 10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertError(InvalidClientMetadataException.class);
    }

    private void redirectOnceThenServeKeys() {
        handler.set(exchange -> {
            if (exchange.getRequestURI().getPath().startsWith("/redirected")) {
                serve(exchange);
                return;
            }
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + port + "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    private static void serve(HttpExchange exchange) throws IOException {
        byte[] bytes = JWKS.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String url() {
        return "http://127.0.0.1:" + port + "/jwks";
    }
}
