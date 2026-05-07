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
package io.gravitee.am.service.cimd;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.model.CimdPreview;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CimdMetadataFetcherTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();

    private Vertx vertx;
    private WebClient webClient;
    private CimdMetadataFetcher fetcher;

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
        fetcher = new CimdMetadataFetcher(webClient);
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
    public void fetchAndValidate_success() {
        respondJson("""
                {
                  "client_id": "%url%",
                  "client_name": "Acme",
                  "redirect_uris": ["https://acme.example/cb"],
                  "grant_types": ["authorization_code"],
                  "response_types": ["code"],
                  "scope": "openid profile",
                  "token_endpoint_auth_method": "none",
                  "logo_uri": "https://acme.example/logo.png"
                }
                """);

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();

        CimdPreview preview = obs.values().get(0);
        assertEquals(url(), preview.url());
        assertEquals("Acme", preview.clientName());
        assertEquals(List.of("https://acme.example/cb"), preview.redirectUris());
        assertEquals(List.of("openid", "profile"), preview.scopes());
        assertEquals("none", preview.tokenEndpointAuthMethod());
        assertFalse(preview.missing().clientName());
        assertFalse(preview.missing().clientId());
        assertNotNull(preview.metadataJson());
    }

    @Test
    public void fetchAndValidate_missingClientIdAndName_marksMissing() {
        respondJson("""
                {
                  "redirect_uris": ["https://acme.example/cb"]
                }
                """);

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertComplete();

        CimdPreview preview = obs.values().get(0);
        assertTrue(preview.missing().clientId());
        assertTrue(preview.missing().clientName());
    }

    @Test
    public void fetchAndValidate_missingRedirectUris_fails() {
        respondJson("{ \"client_id\": \"x\" }");

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_clientSecretForbidden() {
        respondJson("""
                {
                  "redirect_uris": ["https://acme.example/cb"],
                  "client_secret": "leaked"
                }
                """);

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_invalidJson_fails() {
        handler.set(exchange -> {
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_404_fails() {
        handler.set(exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_disabledDomain_fails() {
        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(false), url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_nonHttpUrl_fails() {
        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain(true), "ftp://example.com/").test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_hostNotInAllowedDomains_fails() {
        respondJson("{ \"redirect_uris\": [\"https://acme.example/cb\"] }");

        Domain domain = domain(true);
        domain.getOidc().getCimdSettings().setAllowedDomains(List.of("only-this.example"));

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain, url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void fetchAndValidate_responseExceedsMaxSize_fails() {
        StringBuilder big = new StringBuilder("{ \"redirect_uris\": [\"https://acme.example/cb\"], \"x\": \"");
        big.append("a".repeat(20_000));
        big.append("\" }");
        respondJson(big.toString());

        Domain domain = domain(true);
        domain.getOidc().getCimdSettings().setMaxResponseSizeKb(1);

        TestObserver<CimdPreview> obs = fetcher.fetchAndValidate(domain, url()).test();
        obs.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        obs.assertError(InvalidClientMetadataException.class);
    }

    private void respondJson(String json) {
        handler.set(exchange -> {
            String body = json.replace("%url%", url());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private String url() {
        return "http://127.0.0.1:" + port + "/cimd";
    }

    private Domain domain(boolean enabled) {
        Domain d = new Domain();
        d.setId("test-domain");
        OIDCSettings oidc = new OIDCSettings();
        CIMDSettings cimd = new CIMDSettings();
        cimd.setEnabled(enabled);
        cimd.setAllowUnsecuredHttpUri(true);
        cimd.setAllowPrivateIpAddress(true);
        cimd.setFetchTimeoutMs(2000);
        cimd.setMaxResponseSizeKb(64);
        cimd.setCacheTtlSeconds(60);
        oidc.setCimdSettings(cimd);
        d.setOidc(oidc);
        return d;
    }
}
