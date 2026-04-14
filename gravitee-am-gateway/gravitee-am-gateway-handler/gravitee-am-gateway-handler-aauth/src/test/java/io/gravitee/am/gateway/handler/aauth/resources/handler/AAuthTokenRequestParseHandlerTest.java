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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Tests for {@link AAuthTokenRequestParseHandler}.
 */
public class AAuthTokenRequestParseHandlerTest extends RxWebTestBase {

    private static final String TOKEN_PATH = "/aauth/token";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(TOKEN_PATH)
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create())
                .handler(new AAuthTokenRequestParseHandler())
                .handler(rc -> rc.response().setStatusCode(200).end("ok"));
    }

    @Test
    public void shouldReturn200_withValidResourceToken() throws Exception {
        String body = new JsonObject()
                .put("resource_token", "eyJhbGciOiJFZERTQSJ9.test.sig")
                .encode();

        testPostRequest(body, 200);
    }

    @Test
    public void shouldReturn400_whenResourceTokenMissing() throws Exception {
        String body = new JsonObject()
                .put("justification", "need access")
                .encode();

        JsonObject response = postAndGetJson(body);
        assertEquals("invalid_request", response.getString("error"));
    }

    @Test
    public void shouldReturn400_whenResourceTokenBlank() throws Exception {
        String body = new JsonObject()
                .put("resource_token", "   ")
                .encode();

        JsonObject response = postAndGetJson(body);
        assertEquals("invalid_request", response.getString("error"));
    }

    @Test
    public void shouldReturn400_whenBodyNotJson() throws Exception {
        testRequest(
                HttpMethod.POST,
                TOKEN_PATH,
                req -> {
                    req.putHeader("Content-Type", "application/json");
                    req.end(io.vertx.core.buffer.Buffer.buffer("not json"));
                },
                null,
                400,
                "Bad Request",
                null
        );
    }

    @Test
    public void shouldReturn400_whenBodyEmpty() throws Exception {
        testRequest(
                HttpMethod.POST,
                TOKEN_PATH,
                req -> {
                    req.putHeader("Content-Type", "application/json");
                    req.end(io.vertx.core.buffer.Buffer.buffer(""));
                },
                null,
                400,
                "Bad Request",
                null
        );
    }

    @Test
    public void shouldPreserveOptionalFields() throws Exception {
        String body = new JsonObject()
                .put("resource_token", "test.jwt.token")
                .put("justification", "I need this resource")
                .put("login_hint", "user@example.com")
                .encode();

        testPostRequest(body, 200);
    }

    private void testPostRequest(String body, int expectedStatus) throws Exception {
        testRequest(
                HttpMethod.POST,
                TOKEN_PATH,
                req -> {
                    req.putHeader("Content-Type", "application/json");
                    req.end(io.vertx.core.buffer.Buffer.buffer(body));
                },
                null,
                expectedStatus,
                expectedStatus == 200 ? "OK" : "Bad Request",
                null
        );
    }

    private JsonObject postAndGetJson(String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + server.actualPort() + TOKEN_PATH
        ).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        var stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String responseBody = reader.lines().collect(Collectors.joining("\n"));
            return new JsonObject(responseBody);
        }
    }
}
