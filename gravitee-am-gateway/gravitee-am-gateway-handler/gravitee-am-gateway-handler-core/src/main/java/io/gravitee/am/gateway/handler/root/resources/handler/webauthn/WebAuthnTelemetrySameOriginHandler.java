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

import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures the {@code Origin} header matches the gateway's public origin so browser telemetry cannot be posted
 * from arbitrary external sites (browsers do not send {@code Origin} on navigations the same way; this endpoint
 * is only intended for {@code fetch} / XHR from AM pages, which always include {@code Origin}).
 *
 * @author GraviteeSource Team
 */
public class WebAuthnTelemetrySameOriginHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthnTelemetrySameOriginHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        String origin = ctx.request().getHeader(HttpHeaders.ORIGIN);
        if (!UriBuilderRequest.isRequestOriginAllowed(ctx.request(), origin)) {
            LOGGER.warn("Rejected WebAuthn client telemetry: disallowed or missing Origin");
            ctx.response().setStatusCode(403).end();
            return;
        }
        ctx.next();
    }
}
