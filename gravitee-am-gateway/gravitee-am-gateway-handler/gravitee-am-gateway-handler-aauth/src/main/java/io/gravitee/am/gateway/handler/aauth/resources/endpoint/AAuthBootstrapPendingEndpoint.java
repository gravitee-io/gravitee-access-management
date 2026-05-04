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

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.AAuthBootstrapService;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.BootstrapRequestNotFoundException;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.TooFastException;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Polling endpoint for AAUTH bootstrap requests (GET /aauth/bootstrap/pending/:id).
 * The agent polls this endpoint until the user completes consent.
 * Per AAUTH Bootstrap spec Section 6.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapPendingEndpoint implements Handler<RoutingContext> {

    private final AAuthBootstrapService bootstrapService;

    @Override
    public void handle(RoutingContext ctx) {
        String pendingId = ctx.pathParam("id");
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);

        if (verification == null) {
            sendError(ctx, 401, "unauthorized", "Missing signature");
            return;
        }

        bootstrapService.poll(pendingId, verification.jwkThumbprint())
                .subscribe(
                        request -> {
                            String status = request.getStatus();
                            PendingRequestStatus parsed;
                            try {
                                parsed = PendingRequestStatus.valueOf(status);
                            } catch (IllegalArgumentException e) {
                                log.error("Invalid bootstrap request status in DB: {}", status);
                                sendError(ctx, 500, "server_error", "Invalid bootstrap request state");
                                return;
                            }

                            String pendingUrl = resolvePendingUrl(ctx, pendingId);

                            switch (parsed) {
                                case PENDING -> ctx.response()
                                        .setStatusCode(202)
                                        .putHeader("Location", pendingUrl)
                                        .putHeader("Retry-After", "5")
                                        .putHeader("Cache-Control", "no-store")
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("status", "pending")
                                                .encode());

                                case INTERACTING -> ctx.response()
                                        .setStatusCode(202)
                                        .putHeader("Location", pendingUrl)
                                        .putHeader("Retry-After", "5")
                                        .putHeader("Cache-Control", "no-store")
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("status", "interacting")
                                                .encode());

                                case COMPLETED -> ctx.response()
                                        .setStatusCode(200)
                                        .putHeader("Cache-Control", "no-store")
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("bootstrap_token", request.getBootstrapToken())
                                                .encode());

                                case DENIED -> sendError(ctx, 403, "denied", "User denied the bootstrap request");

                                case EXPIRED -> sendError(ctx, 408, "expired", "Bootstrap request expired");

                                case CANCELLED -> sendError(ctx, 410, "cancelled", "Agent cancelled the request");

                                default -> sendError(ctx, 500, "server_error", "Unexpected status: " + status);
                            }
                        },
                        err -> {
                            if (err instanceof TooFastException) {
                                ctx.response()
                                        .setStatusCode(429)
                                        .putHeader("Retry-After", "5")
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("error", "slow_down").encode());
                            } else if (err instanceof BootstrapRequestNotFoundException) {
                                sendError(ctx, 410, "invalid_code", "Unknown or consumed bootstrap request");
                            } else if (err instanceof SecurityException) {
                                sendError(ctx, 403, "forbidden", err.getMessage());
                            } else {
                                log.error("Error polling bootstrap request: {}", err.getMessage());
                                sendError(ctx, 500, "server_error", "Internal error");
                            }
                        },
                        () -> {
                            // Empty = not found or expired
                            sendError(ctx, 410, "invalid_code", "Unknown or consumed bootstrap request");
                        }
                );
    }

    private String resolvePendingUrl(RoutingContext ctx, String pendingId) {
        try {
            String basePath = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH));
            return basePath + "/aauth/bootstrap/pending/" + pendingId;
        } catch (Exception e) {
            return "/aauth/bootstrap/pending/" + pendingId;
        }
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
