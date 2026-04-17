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
 * DELETE /aauth/pending/:id — agent cancels the pending request.
 * Per spec Section 7.3.3.3.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthPendingDeleteEndpoint implements Handler<RoutingContext> {

    private final AAuthPendingRequestService pendingService;

    @Override
    public void handle(RoutingContext ctx) {
        String pendingId = ctx.pathParam("id");
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);

        if (verification == null) {
            ctx.response().setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "unauthorized").encode());
            return;
        }

        pendingService.cancel(pendingId, verification.jwkThumbprint())
                .subscribe(
                        () -> ctx.response().setStatusCode(204).end(),
                        err -> {
                            if (err instanceof PendingRequestNotFoundException) {
                                ctx.response().setStatusCode(410)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("error", "invalid_code").encode());
                            } else if (err instanceof SecurityException) {
                                ctx.response().setStatusCode(403)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("error", "forbidden").encode());
                            } else {
                                log.error("Error cancelling pending request: {}", err.getMessage());
                                ctx.response().setStatusCode(500)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("error", "server_error").encode());
                            }
                        }
                );
    }
}
