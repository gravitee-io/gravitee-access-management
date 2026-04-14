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

import io.gravitee.am.gateway.handler.aauth.model.AAuthTokenRequest;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses the JSON body of {@code POST /aauth/token} into an {@link AAuthTokenRequest}
 * and stores it in the routing context. Returns 400 {@code invalid_request} if the
 * body is missing or {@code resource_token} is absent.
 * <p>
 * Per AAUTH spec Section 7.1.3.
 */
@Slf4j
public class AAuthTokenRequestParseHandler implements Handler<RoutingContext> {

    public static final String AAUTH_TOKEN_REQUEST_CONTEXT_KEY = "aauth.tokenRequest";

    @Override
    public void handle(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                sendError(ctx, "Request body is required");
                return;
            }

            String resourceToken = body.getString("resource_token");
            if (resourceToken == null || resourceToken.isBlank()) {
                sendError(ctx, "resource_token is required");
                return;
            }

            var request = new AAuthTokenRequest(
                    resourceToken,
                    body.getString("upstream_token"),
                    body.getString("justification"),
                    body.getString("login_hint"),
                    body.getString("tenant"),
                    body.getString("domain_hint")
            );

            ctx.put(AAUTH_TOKEN_REQUEST_CONTEXT_KEY, request);
            ctx.next();

        } catch (DecodeException e) {
            log.debug("Failed to parse token request body: {}", e.getMessage());
            sendError(ctx, "Invalid JSON body");
        }
    }

    private void sendError(RoutingContext ctx, String description) {
        ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "no-store")
                .end(new JsonObject()
                        .put("error", "invalid_request")
                        .put("error_description", description)
                        .encode());
    }
}
