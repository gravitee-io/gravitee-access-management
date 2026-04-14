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

import io.gravitee.am.gateway.handler.aauth.model.AAuthTokenRequest;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenException;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenValidator;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Terminal handler for {@code POST /aauth/token}. Validates the resource token,
 * then issues an {@code aa-auth+jwt} auth token.
 * <p>
 * Phase 6: machine-to-machine only (scope-based, no user binding).
 * User-bound flows (202 deferred) will be added in Phase 8.
 * <p>
 * Per AAUTH spec Sections 7.1.3 and 7.1.4.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthTokenEndpoint implements Handler<RoutingContext> {

    private final ResourceTokenValidator resourceTokenValidator;
    private final AAuthTokenService tokenService;

    @Override
    public void handle(RoutingContext ctx) {
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);
        AAuthTokenRequest tokenRequest = ctx.get(AAuthTokenRequestParseHandler.AAUTH_TOKEN_REQUEST_CONTEXT_KEY);

        // Resolve PS issuer URL dynamically from the request context
        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        try {
            // Validate resource token per spec Section 6.6.2
            var rtClaims = resourceTokenValidator.validate(tokenRequest.resourceToken(), verification, psIssuerUrl);

            // Phase 6: M2M only — no sub, scope from resource_token
            // Phase 8 will add user-bound flows (202 deferred authorization)
            tokenService.createAuthToken(rtClaims, verification, psIssuerUrl)
                    .subscribe(
                            response -> ctx.response()
                                    .setStatusCode(200)
                                    .putHeader("Content-Type", "application/json")
                                    .putHeader("Cache-Control", "no-store")
                                    .end(new JsonObject()
                                            .put("auth_token", response.authToken())
                                            .put("expires_in", response.expiresIn())
                                            .encode()),
                            err -> {
                                log.error("Failed to create auth token: {}", err.getMessage());
                                sendError(ctx, 500, "server_error", "Internal error creating auth token");
                            }
                    );

        } catch (ResourceTokenException e) {
            log.debug("Resource token validation failed: {} — {}", e.getErrorCode(), e.getMessage());
            sendError(ctx, 400, e.getErrorCode(), e.getMessage());
        }
    }

    /**
     * Resolve the PS issuer URL from the request context, using the same pattern
     * as {@link AAuthPSMetadataEndpoint} to handle proxy headers correctly.
     */
    private String resolvePsIssuerUrl(RoutingContext ctx) {
        try {
            String basePath = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH));
            return basePath + "/aauth";
        } catch (Exception e) {
            log.warn("Unable to resolve PS issuer URL from request context", e);
            return "/aauth";
        }
    }

    private void sendError(RoutingContext ctx, int statusCode, String error, String description) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "no-store")
                .end(new JsonObject()
                        .put("error", error)
                        .put("error_description", description)
                        .encode());
    }
}
