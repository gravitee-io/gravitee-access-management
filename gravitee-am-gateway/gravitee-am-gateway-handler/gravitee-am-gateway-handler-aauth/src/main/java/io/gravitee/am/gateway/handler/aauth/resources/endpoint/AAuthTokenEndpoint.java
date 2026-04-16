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
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthAgentResolveHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenClaims;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenException;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenValidator;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Application;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Terminal handler for {@code POST /aauth/token}. Validates the resource token,
 * then either issues an auth token directly (M2M) or returns 202 for deferred
 * user-bound authorization.
 * <p>
 * Per AAUTH spec Sections 7.1.3 and 7.1.4.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthTokenEndpoint implements Handler<RoutingContext> {

    private final ResourceTokenValidator resourceTokenValidator;
    private final AAuthTokenService tokenService;
    private final AAuthPendingRequestService pendingService;
    private final String domainId;
    private final int pendingRequestTtl;

    @Override
    public void handle(RoutingContext ctx) {
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);
        AAuthTokenRequest tokenRequest = ctx.get(AAuthTokenRequestParseHandler.AAUTH_TOKEN_REQUEST_CONTEXT_KEY);

        // Resolve PS issuer URL dynamically from the request context
        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        try {
            // Validate resource token per spec Section 6.6.2
            var rtClaims = resourceTokenValidator.validate(tokenRequest.resourceToken(), verification, psIssuerUrl);

            // Always trigger deferred authorization (202 + interaction).
            // The PS requires user consent for every token request. The consent
            // cache at the interaction endpoint handles returning users transparently.
            // Re-authorization (Section 7.7) follows the same path with a fresh resource_token.
            startDeferredFlow(ctx, verification, tokenRequest, rtClaims, psIssuerUrl);

        } catch (ResourceTokenException e) {
            log.debug("Resource token validation failed: {} — {}", e.getErrorCode(), e.getMessage());
            sendError(ctx, 400, e.getErrorCode(), e.getMessage());
        }
    }

    private void startDeferredFlow(RoutingContext ctx, VerificationResult verification,
                                    AAuthTokenRequest tokenRequest, ResourceTokenClaims rtClaims,
                                    String psIssuerUrl) {
        Application app = ctx.get(AAuthAgentResolveHandler.AAUTH_APPLICATION_CONTEXT_KEY);
        if (app == null) {
            // Agent identity is required for the deferred flow (spec Section 7.1.3 requires scheme=jwt).
            // Without an identified agent, the interaction endpoint cannot resolve the Application
            // for login/consent configuration.
            sendError(ctx, 400, "invalid_request",
                    "Agent identity is required. The PS token endpoint requires an identified agent.");
            return;
        }

        pendingService.create(
                domainId,
                verification.agentIdentityUrl(),
                rtClaims.agent(),
                verification.jwkThumbprint(),
                verification.publicKey(),
                app.getId(),
                rtClaims.iss(),
                rtClaims.scope(),
                tokenRequest.justification(),
                psIssuerUrl,
                pendingRequestTtl
        ).subscribe(
                pending -> {
                    String basePath = resolvePsIssuerUrl(ctx).replace("/aauth", "");
                    String pendingUrl = basePath + "/aauth/pending/" + pending.getId();
                    String interactUrl = basePath + "/aauth/interact";

                    ctx.response()
                            .setStatusCode(202)
                            .putHeader("Location", pendingUrl)
                            .putHeader("Retry-After", "0")
                            .putHeader("Cache-Control", "no-store")
                            .putHeader("AAuth-Requirement",
                                    "requirement=interaction; url=\"" + interactUrl + "\"; code=\"" + pending.getInteractionCode() + "\"")
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "pending").encode());
                },
                err -> {
                    log.error("Failed to create pending request: {}", err.getMessage());
                    sendError(ctx, 500, "server_error", "Internal error creating pending request");
                }
        );
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
