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
package io.gravitee.am.gateway.handler.vertx;


import io.gravitee.am.model.Domain;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RxWebTestBase extends RxVertxTestBase {

    protected static Set<HttpMethod> METHODS = new HashSet<>(Arrays.asList(HttpMethod.DELETE, HttpMethod.GET,
            HttpMethod.HEAD, HttpMethod.PATCH, HttpMethod.OPTIONS, HttpMethod.TRACE, HttpMethod.POST, HttpMethod.PUT));

    protected HttpServer server;
    protected HttpClient client;
    protected Router router;
    protected Domain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set security domain handler
        router = router();

        server = vertx.createHttpServer(getHttpServerOptions());
        client = vertx.createHttpClient(getHttpClientOptions());
        CountDownLatch latch = new CountDownLatch(1);
        server.requestHandler(router::accept).listen(onSuccess(res -> latch.countDown()));
        awaitLatch(latch);
    }

    protected Router router() {
        return Router.router(vertx);
    }

    protected VertxSecurityDomainHandler handler() {
        VertxSecurityDomainHandler handler = new VertxSecurityDomainHandler();
        handler.setVertx(vertx);

        domain = createDomain();
        handler.setDomain(domain);

        return handler;
    }

    protected Domain createDomain() {
        Domain domain = new Domain();
        domain.setPath("default-domain");
        return domain;
    }

    protected HttpServerOptions getHttpServerOptions() {
        return new HttpServerOptions().setPort(8080).setHost("localhost");
    }

    protected HttpClientOptions getHttpClientOptions() {
        return new HttpClientOptions().setDefaultPort(8080);
    }

    @Override
    public void tearDown() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (IllegalStateException e) {
            }
        }
        if (server != null) {
            CountDownLatch latch = new CountDownLatch(1);
            server.close((asyncResult) -> {
                assertTrue(asyncResult.succeeded());
                latch.countDown();
            });
            awaitLatch(latch);
        }
        super.tearDown();
    }

    protected void testRequest(HttpMethod method, String path, int statusCode, String statusMessage) throws Exception {
        testRequest(method, path, null, statusCode, statusMessage, null);
    }

    protected void testRequest(HttpMethod method, String path, int statusCode, String statusMessage,
                               String responseBody) throws Exception {
        testRequest(method, path, null, statusCode, statusMessage, responseBody);
    }

    protected void testRequest(HttpMethod method, String path, int statusCode, String statusMessage,
                               Buffer responseBody) throws Exception {
        testRequestBuffer(method, path, null, null, statusCode, statusMessage, responseBody);
    }

    protected void testRequestWithContentType(HttpMethod method, String path, String contentType, int statusCode, String statusMessage) throws Exception {
        testRequest(method, path, req -> req.putHeader("content-type", contentType), statusCode, statusMessage, null);
    }

    protected void testRequestWithAccepts(HttpMethod method, String path, String accepts, int statusCode, String statusMessage) throws Exception {
        testRequest(method, path, req -> req.putHeader("accept", accepts), statusCode, statusMessage, null);
    }

    protected void testRequestWithCookies(HttpMethod method, String path, String cookieHeader, int statusCode, String statusMessage) throws Exception {
        testRequest(method, path, req -> req.putHeader("cookie", cookieHeader), statusCode, statusMessage, null);
    }

    protected void testRequest(HttpMethod method, String path, Consumer<HttpClientRequest> requestAction,
                               int statusCode, String statusMessage,
                               String responseBody) throws Exception {
        testRequest(method, path, requestAction, null, statusCode, statusMessage, responseBody);
    }

    protected void testRequest(HttpMethod method, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction,
                               int statusCode, String statusMessage,
                               String responseBody) throws Exception {
        testRequestBuffer(method, path, requestAction, responseAction, statusCode, statusMessage, responseBody != null ? Buffer.buffer(responseBody) : null, true);
    }

    protected void testRequestBuffer(HttpMethod method, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction,
                                     int statusCode, String statusMessage,
                                     Buffer responseBodyBuffer) throws Exception {
        testRequestBuffer(method, path, requestAction, responseAction, statusCode, statusMessage, responseBodyBuffer, false);
    }

    protected void testRequestBuffer(HttpMethod method, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction,
                                     int statusCode, String statusMessage,
                                     Buffer responseBodyBuffer, boolean normalizeLineEndings) throws Exception {
        testRequestBuffer(client, method, 8080, path, requestAction, responseAction, statusCode, statusMessage, responseBodyBuffer, normalizeLineEndings);
    }

    protected void testRequestBuffer(HttpClient client, HttpMethod method, int port, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction,
                                     int statusCode, String statusMessage,
                                     Buffer responseBodyBuffer) throws Exception {
        testRequestBuffer(client, method, port, path, requestAction, responseAction, statusCode, statusMessage, responseBodyBuffer, false);
    }

    protected void testRequestBuffer(HttpClient client, HttpMethod method, int port, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction,
                                     int statusCode, String statusMessage,
                                     Buffer responseBodyBuffer, boolean normalizeLineEndings) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HttpClientRequest req = client.request(method, port, "localhost", path, resp -> {
            assertEquals(statusCode, resp.statusCode());
            assertEquals(statusMessage, resp.statusMessage());
            if (responseAction != null) {
                responseAction.accept(resp);
            }
            if (responseBodyBuffer == null) {
                latch.countDown();
            } else {
                resp.bodyHandler(buff -> {
                    if (normalizeLineEndings) {
                        buff = normalizeLineEndingsFor(buff);
                    }
                    assertEquals(responseBodyBuffer, buff);
                    latch.countDown();
                });
            }
        });
        if (requestAction != null) {
            requestAction.accept(req);
        }
        req.end();
        awaitLatch(latch);
    }

    protected static Buffer normalizeLineEndingsFor(Buffer buff) {
        int buffLen = buff.length();
        Buffer normalized = Buffer.buffer(buffLen);
        for (int i = 0; i < buffLen; i++) {
            short unsignedByte = buff.getUnsignedByte(i);
            if (unsignedByte != '\r' || i + 1 == buffLen || buff.getUnsignedByte(i + 1) != '\n') {
                normalized.appendUnsignedByte(unsignedByte);
            }
        }
        return normalized;
    }

    protected void testSyncRequest(String httpMethod, String path, int statusCode, String statusMessage, String responseBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + this.server.actualPort() + path).openConnection();
        connection.setRequestMethod(httpMethod);

        assertEquals(statusCode, connection.getResponseCode());
        if (connection.getResponseCode() < 400) { // So dummy compare
            assertEquals(statusMessage, connection.getResponseMessage());

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            assertEquals(responseBody, response.toString());
        } else {
            assertEquals(statusMessage, connection.getResponseMessage());

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            assertEquals(responseBody, response.toString());
        }
    }
}
