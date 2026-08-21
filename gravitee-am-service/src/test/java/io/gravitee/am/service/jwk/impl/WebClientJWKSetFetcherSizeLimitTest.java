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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Response-size enforcement, exercised against a real HTTP server so the streaming abort
 * is genuinely covered rather than mocked away.
 */
public class WebClientJWKSetFetcherSizeLimitTest {

    private static final String SMALL_JWKS =
            "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"KID\",\"n\":\"modulus\",\"e\":\"exponent\"}]}";

    private HttpServer server;
    private int port;
    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();

    private Vertx vertx;
    private WebClient webClient;
    private WebClientJWKSetFetcher fetcher;

    @Before
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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
        fetcher = new WebClientJWKSetFetcher(webClient);
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
    public void shouldRejectResponseLargerThanTheLimit() {
        StringBuilder padded = new StringBuilder("{\"keys\":[],\"padding\":\"");
        padded.append("a".repeat(20_000));
        padded.append("\"}");
        respond(padded.toString());

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url(), 1024L).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertError(InvalidClientMetadataException.class);
        observer.assertError(error -> error.getMessage().contains("maximum allowed response size"));
    }

    @Test
    public void shouldReturnKeysWhenResponseFitsWithinTheLimit() {
        respond(SMALL_JWKS);

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url(), 1024L).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(response -> response.jwkSet().getKeys().size() == 1
                && "KID".equals(response.jwkSet().getKeys().get(0).getKid()));
    }

    @Test
    public void shouldNotBoundTheResponseWhenTheLimitIsNotPositive() {
        StringBuilder padded = new StringBuilder("{\"keys\":[],\"padding\":\"");
        padded.append("a".repeat(20_000));
        padded.append("\"}");
        respond(padded.toString());

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url(), 0L).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(response -> response.jwkSet().getKeys().isEmpty());
    }

    @Test
    public void shouldRejectNonOkStatusOnTheBoundedPath() {
        handler.set(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        TestObserver<JWKSetFetchResponse> observer = fetcher.getKeys(url(), 1024L).test();
        observer.awaitDone(5, TimeUnit.SECONDS);

        observer.assertError(InvalidClientMetadataException.class);
    }

    private void respond(String json) {
        handler.set(exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private String url() {
        return "http://127.0.0.1:" + port + "/jwks";
    }
}
