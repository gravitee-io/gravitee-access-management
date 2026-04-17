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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.pending.PendingRequestNotFoundException;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * POST /aauth/pending/:id — agent responds to a clarification question.
 * Per spec Section 7.3.3.1: POST with {@code clarification_response} field.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthPendingPostEndpoint implements Handler<RoutingContext> {

    private final AAuthPendingRequestService pendingService;

    @Override
    public void handle(RoutingContext ctx) {
        String pendingId = ctx.pathParam("id");
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);

        if (verification == null) {
            sendError(ctx, 401, "unauthorized", "Missing signature");
            return;
        }

        JsonObject body = ctx.body() != null ? ctx.body().asJsonObject() : null;
        if (body == null) {
            sendError(ctx, 400, "invalid_request", "Missing request body");
            return;
        }

        String clarificationResponse = body.getString("clarification_response");
        if (clarificationResponse == null || clarificationResponse.isBlank()) {
            sendError(ctx, 400, "invalid_request", "Missing clarification_response field");
            return;
        }

        pendingService.respondClarification(pendingId, verification.jwkThumbprint(), clarificationResponse)
                .subscribe(
                        request -> ctx.response()
                                .setStatusCode(202)
                                .putHeader("Content-Type", "application/json")
                                .putHeader("Cache-Control", "no-store")
                                .end(new JsonObject().put("status", "interacting").encode()),
                        err -> {
                            if (err instanceof PendingRequestNotFoundException) {
                                sendError(ctx, 410, "invalid_code", "Unknown or consumed pending request");
                            } else if (err instanceof SecurityException) {
                                sendError(ctx, 403, "forbidden", err.getMessage());
                            } else if (err instanceof IllegalStateException) {
                                sendError(ctx, 409, "invalid_state", err.getMessage());
                            } else {
                                log.error("Error responding to clarification: {}", err.getMessage());
                                sendError(ctx, 500, "server_error", "Internal error");
                            }
                        }
                );
    }

    private void sendError(RoutingContext ctx, int status, String error, String description) {
        ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "no-store")
                .end(new JsonObject()
                        .put("error", error)
                        .put("error_description", description)
                        .encode());
    }
}
