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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.ORIGIN;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnTelemetrySameOriginHandlerTest extends RxWebTestBase {

    private final WebAuthnTelemetrySameOriginHandler handler = new WebAuthnTelemetrySameOriginHandler();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route().handler(BodyHandler.create());
    }

    private String sameOrigin() {
        return "http://localhost:" + server.actualPort();
    }

    @Test
    public void shouldCallNext_whenOriginMatchesGateway() throws Exception {
        router.route(HttpMethod.POST, "/telemetry")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(204).end());

        testRequest(
                HttpMethod.POST,
                "/telemetry",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader(ORIGIN, sameOrigin());
                    req.write(Buffer.buffer("{}"));
                },
                null,
                204,
                "No Content",
                null);
    }

    @Test
    public void shouldReturn403_whenOriginMissing() throws Exception {
        router.route(HttpMethod.POST, "/telemetry")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(204).end());

        testRequest(
                HttpMethod.POST,
                "/telemetry",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.write(Buffer.buffer("{}"));
                },
                null,
                403,
                "Forbidden",
                null);
    }

    @Test
    public void shouldReturn403_whenOriginMismatch() throws Exception {
        router.route(HttpMethod.POST, "/telemetry")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(204).end());

        testRequest(
                HttpMethod.POST,
                "/telemetry",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader(ORIGIN, "https://evil.example");
                    req.write(Buffer.buffer("{}"));
                },
                null,
                403,
                "Forbidden",
                null);
    }
}
