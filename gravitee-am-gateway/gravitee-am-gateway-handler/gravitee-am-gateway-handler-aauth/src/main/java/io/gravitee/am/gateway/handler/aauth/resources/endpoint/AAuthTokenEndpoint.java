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
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenClaims;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenException;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenValidator;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapBindingRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.reactivex.rxjava3.core.Maybe;
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
    private final AAuthBootstrapBindingRepository bindingRepository;
    private final AAuthConsentService consentService;
    private final String domainId;
    private final int pendingRequestTtl;

    @Override
    public void handle(RoutingContext ctx) {
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);
        AAuthTokenRequest tokenRequest = ctx.get(AAuthTokenRequestParseHandler.AAUTH_TOKEN_REQUEST_CONTEXT_KEY);

        // Resolve PS issuer URL dynamically from the request context
        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        try {
            // Per spec Section 7.1.3: the PS token endpoint requires scheme=jwt
            if (!"jwt".equals(verification.scheme())) {
                sendError(ctx, 401, "invalid_request",
                        "The PS token endpoint requires scheme=jwt (agent token). Received: " + verification.scheme());
                return;
            }

            // Validate resource token per spec Section 6.6.2
            var rtClaims = resourceTokenValidator.validate(tokenRequest.resourceToken(), verification, psIssuerUrl);

            // Per spec Section 6.6.1: "The PS SHOULD remember prior consent decisions within a
            // mission so the user is not re-prompted when the agent resubmits a request for the
            // same resource and scope." Look up the bootstrap binding to discover which user
            // this agent represents; if the user has previously approved the requested scope,
            // mint the auth_token synchronously and return 200. Otherwise fall through to the
            // 202 + interaction flow.
            tryDirectGrantOrDefer(ctx, verification, tokenRequest, rtClaims, psIssuerUrl);

        } catch (ResourceTokenException e) {
            log.debug("Resource token validation failed: {} — {}", e.getErrorCode(), e.getMessage());
            sendError(ctx, 400, e.getErrorCode(), e.getMessage());
        }
    }

    private void tryDirectGrantOrDefer(RoutingContext ctx, VerificationResult verification,
                                       AAuthTokenRequest tokenRequest, ResourceTokenClaims rtClaims,
                                       String psIssuerUrl) {
        Application app = ctx.get(AAuthAgentResolveHandler.AAUTH_APPLICATION_CONTEXT_KEY);
        // ScopeApproval rows are keyed by the OAuth client_id (Application.clientId()), not the
        // internal Application UUID — match how AAuthConsentService.saveConsent stored them so
        // the lookup actually hits.
        String oauthClientId = app == null ? null : app.clientId();
        if (oauthClientId == null || verification.agentServerUrl() == null || rtClaims.agent() == null) {
            // Without an identified agent/AS we can't look up the binding — fall straight
            // through to the deferred flow, which itself enforces the "agent required" error.
            startDeferredFlow(ctx, verification, tokenRequest, rtClaims, psIssuerUrl);
            return;
        }

        bindingRepository.findByDomainAndAgentServerUrlAndAgentIdentifier(
                        domainId, verification.agentServerUrl(), rtClaims.agent())
                .flatMap(binding -> hasApprovedScope(binding, oauthClientId, rtClaims.scope())
                        .filter(Boolean::booleanValue)
                        .map(approved -> binding))
                .flatMapSingle(binding -> tokenService.createAuthToken(
                        rtClaims, verification, psIssuerUrl, binding.getUserId()))
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
                            log.warn("Direct-grant path failed, falling back to interaction: {}", err.getMessage());
                            startDeferredFlow(ctx, verification, tokenRequest, rtClaims, psIssuerUrl);
                        },
                        // No binding or no matching consent — start the interactive flow.
                        () -> startDeferredFlow(ctx, verification, tokenRequest, rtClaims, psIssuerUrl)
                );
    }

    /** Resolve to {@code true} iff the user named by {@code binding} has current, non-expired
     *  consent for ALL scopes requested by the resource token. The resource_token's {@code scope}
     *  claim is a space-separated list (per OAuth convention); every individual scope must be
     *  in the user's approved set. Resolves to empty when any scope is missing, which is what
     *  triggers the fallback to the 202 interactive flow. */
    private Maybe<Boolean> hasApprovedScope(AAuthBootstrapBinding binding, String clientId, String requestedScopes) {
        if (requestedScopes == null || requestedScopes.isBlank()) {
            return Maybe.empty();
        }
        String[] required = requestedScopes.trim().split("\\s+");
        return consentService.checkConsent(UserId.internal(binding.getUserId()), clientId)
                .flatMapMaybe(approved -> {
                    for (String scope : required) {
                        if (!approved.contains(scope)) return Maybe.empty();
                    }
                    return Maybe.just(Boolean.TRUE);
                });
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
                verification.agentServerUrl(),
                rtClaims.agent(),
                verification.jwkThumbprint(),
                verification.publicKey(),
                app.getId(),
                rtClaims.iss(),
                rtClaims.scope(),
                tokenRequest.justification(),
                tokenRequest.loginHint(),
                tokenRequest.domainHint(),
                tokenRequest.tenant(),
                parseClarificationSupported(ctx),
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
    /**
     * Parse the AAuth-Capabilities header to check if the agent supports clarification.
     * Per spec Section 12.1, the header is a comma-separated list of capability tokens.
     */
    private boolean parseClarificationSupported(RoutingContext ctx) {
        String capabilities = ctx.request().getHeader("AAuth-Capabilities");
        if (capabilities == null || capabilities.isBlank()) return false;
        for (String cap : capabilities.split(",")) {
            if ("clarification".equalsIgnoreCase(cap.trim())) return true;
        }
        return false;
    }

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
