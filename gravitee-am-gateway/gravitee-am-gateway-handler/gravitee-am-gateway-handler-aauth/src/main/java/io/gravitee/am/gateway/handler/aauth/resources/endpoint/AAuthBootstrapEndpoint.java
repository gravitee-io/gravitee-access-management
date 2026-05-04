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
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.AAuthBootstrapService;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.BootstrapBindingConflictException;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.BootstrapMetadataException;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.BootstrapRequestNotFoundException;
import io.gravitee.am.gateway.handler.aauth.service.registry.AAuthAgentRegistry;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.util.AAuthIdentifierValidator;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Terminal handler for {@code POST /aauth/bootstrap}. Handles two request types:
 * <ul>
 *   <li><b>Initial request</b> (hwk scheme, JSON body): Creates a bootstrap request
 *       and returns 202 with Location + AAuth-Requirement headers.</li>
 *   <li><b>Completion announcement</b> (jwt scheme, empty body): Records the binding
 *       and returns 204 No Content.</li>
 * </ul>
 * Per AAUTH Bootstrap spec Section 6.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapEndpoint implements Handler<RoutingContext> {

    private static final int MAX_HINT_LENGTH = 256;

    private final AAuthBootstrapService bootstrapService;
    private final AAuthAgentRegistry agentRegistry;
    private final Domain domain;

    @Override
    public void handle(RoutingContext ctx) {
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);

        if (verification == null) {
            sendError(ctx, 401, "unauthorized", "Missing signature");
            return;
        }

        if ("jwt".equals(verification.scheme()) && isEmptyBody(ctx)) {
            handleAnnouncement(ctx, verification);
        } else if ("hwk".equals(verification.scheme())) {
            handleInitialRequest(ctx, verification);
        } else {
            sendError(ctx, 400, "invalid_request",
                    "Bootstrap endpoint requires hwk scheme (initial) or jwt scheme with empty body (announcement)");
        }
    }

    /**
     * Handle the initial bootstrap request (hwk scheme).
     * Parses agent_server from the body, creates a bootstrap request, returns 202.
     */
    private void handleInitialRequest(RoutingContext ctx, VerificationResult verification) {
        JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            sendError(ctx, 400, "invalid_request", "Invalid JSON body");
            return;
        }

        if (body == null) {
            sendError(ctx, 400, "invalid_request", "Request body is required");
            return;
        }

        String agentServerUrl = body.getString("agent_server");
        boolean allowInsecure = domain.getAauth() != null && domain.getAauth().isAllowInsecureAgentServer();
        String validationError = AAuthIdentifierValidator.validateAgentServerUrl(agentServerUrl, allowInsecure);
        if (validationError != null) {
            sendError(ctx, 400, "invalid_request", validationError);
            return;
        }

        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        // The ephemeral public key JWK and thumbprint come from the hwk verification
        String ephemeralKeyJwk = serializePublicKeyAsJwk(verification);
        String ephemeralKeyThumbprint = verification.jwkThumbprint();

        String domainHint = body.getString("domain_hint");
        String loginHint = body.getString("login_hint");
        String tenant = body.getString("tenant");

        // B2B hints are optional, free-form strings (DNS domain, email, tenant id).
        // We don't consume them for identity selection yet (forward-compat — see Phase 11),
        // but we cap their length to keep the request body bounded and persisted columns sane.
        String hintLengthError = checkHintLength("domain_hint", domainHint);
        if (hintLengthError == null) hintLengthError = checkHintLength("login_hint", loginHint);
        if (hintLengthError == null) hintLengthError = checkHintLength("tenant", tenant);
        if (hintLengthError != null) {
            sendError(ctx, 400, "invalid_request", hintLengthError);
            return;
        }

        int ttlSeconds = domain.getAauth() != null ? domain.getAauth().getPendingRequestTtl() : 600;

        // Auto-create the AAUTH_AGENT Application early so the ClientSyncService
        // has time to propagate it before the user opens the interact URL.
        VerificationResult syntheticVerification = new VerificationResult(
                "hwk", "sig", null, null, agentServerUrl, null);
        agentRegistry.resolveOrCreate(syntheticVerification, domain.getId())
                .ignoreElement()
                .andThen(bootstrapService.create(
                        domain.getId(),
                        agentServerUrl,
                        ephemeralKeyJwk,
                        ephemeralKeyThumbprint,
                        domainHint,
                        loginHint,
                        tenant,
                        ttlSeconds
                )).subscribe(
                request -> {
                    String basePath = resolvePsIssuerUrl(ctx).replace("/aauth", "");
                    String pendingUrl = basePath + "/aauth/bootstrap/pending/" + request.getId();
                    String interactUrl = basePath + "/aauth/bootstrap-interact";

                    ctx.response()
                            .setStatusCode(202)
                            .putHeader("Location", pendingUrl)
                            .putHeader("Retry-After", "0")
                            .putHeader("Cache-Control", "no-store")
                            .putHeader("AAuth-Requirement",
                                    "requirement=interaction; url=\"" + interactUrl + "\"; code=\"" + request.getInteractionCode() + "\"")
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "pending").encode());
                },
                err -> {
                    if (err instanceof BootstrapMetadataException metaErr) {
                        // Fail-closed metadata path: the AS is unreachable, returns invalid
                        // metadata, or its issuer doesn't match the requested agent_server URL.
                        sendError(ctx, 400, "invalid_request", metaErr.getErrorCode());
                        return;
                    }
                    log.error("Failed to create bootstrap request", err);
                    sendError(ctx, 500, "server_error", "Internal error creating bootstrap request");
                }
        );
    }

    /**
     * Handle the completion announcement (jwt scheme, empty body).
     * Verifies the agent_token's PS claim matches this PS, records the binding.
     */
    private void handleAnnouncement(RoutingContext ctx, VerificationResult verification) {
        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        // All four fields below are extracted from the verified agent_token JWT in JWTScheme:
        //   agentIdentifier        = agent_token.sub        (aauth:local@domain)
        //   agentServerUrl         = agent_token.iss        (audited via the AS's JWKS)
        //   ephemeralKeyThumbprint = thumbprint(cnf.jwk)
        //   agentTokenPs           = agent_token.ps         (the PS the AS believes minted the binding)
        // Signature verification has already passed, so we can trust these claim values.
        String agentIdentifier = verification.agentIdentifier();
        String agentServerUrl = verification.agentServerUrl();
        String ephemeralKeyThumbprint = verification.jwkThumbprint();
        String agentTokenPs = verification.agentTokenPs();

        if (agentServerUrl == null || agentServerUrl.isBlank()) {
            sendError(ctx, 400, "invalid_request", "Agent server URL could not be determined from the agent token");
            return;
        }

        // Spec §6.7 step 3: verify agent_token.ps == this PS URL.
        // Without this check, an agent_token minted by a different (PS, AS) pair could
        // be accepted as long as its ephemeral thumbprint happens to match a pending record.
        if (agentTokenPs == null || agentTokenPs.isBlank()) {
            sendError(ctx, 400, "invalid_request",
                    "agent_token is missing the required ps claim");
            return;
        }
        if (!agentTokenPs.equals(psIssuerUrl)) {
            log.warn("Bootstrap announcement rejected: agent_token.ps={} does not match this PS={}",
                    agentTokenPs, psIssuerUrl);
            sendError(ctx, 400, "invalid_request",
                    "agent_token.ps does not match this Person Server");
            return;
        }

        bootstrapService.announce(
                domain.getId(),
                ephemeralKeyThumbprint,
                agentIdentifier,
                agentServerUrl
        ).subscribe(
                () -> ctx.response()
                        .setStatusCode(204)
                        .putHeader("Cache-Control", "no-store")
                        .end(),
                err -> {
                    if (err instanceof BootstrapRequestNotFoundException) {
                        // No bootstrap record correlates to the announcement's ephemeral key
                        // (record purged after expireAt, or thumbprint never matched).
                        sendError(ctx, 404, "not_found",
                                "No bootstrap record matches the agent's ephemeral key");
                    } else if (err instanceof BootstrapBindingConflictException) {
                        // AS announced a different agent_identifier for an existing
                        // (user, agent_server) binding — protocol violation.
                        log.warn("Bootstrap binding conflict: {}", err.getMessage());
                        sendError(ctx, 409, "binding_conflict", err.getMessage());
                    } else {
                        log.error("Failed to process bootstrap announcement", err);
                        sendError(ctx, 500, "server_error",
                                "Internal error processing announcement");
                    }
                }
        );
    }

    /**
     * Serialize the verification's public key information as a JWK JSON string.
     * For hwk scheme, the key parameters are available from the verification result.
     * We reconstruct the JWK from the public key using Nimbus.
     */
    private String serializePublicKeyAsJwk(VerificationResult verification) {
        try {
            java.security.PublicKey publicKey = verification.publicKey();
            com.nimbusds.jose.jwk.JWK jwk;

            if (publicKey instanceof java.security.interfaces.ECPublicKey ecKey) {
                com.nimbusds.jose.jwk.Curve curve = com.nimbusds.jose.jwk.Curve.forECParameterSpec(ecKey.getParams());
                jwk = new com.nimbusds.jose.jwk.ECKey.Builder(curve, ecKey).build();
            } else {
                // For Ed25519 / OKP keys, reconstruct from raw bytes
                byte[] encoded = publicKey.getEncoded();
                byte[] raw = new byte[32];
                System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
                com.nimbusds.jose.util.Base64URL x = com.nimbusds.jose.util.Base64URL.encode(raw);
                jwk = new com.nimbusds.jose.jwk.OctetKeyPair.Builder(
                        com.nimbusds.jose.jwk.Curve.Ed25519, x).build();
            }
            return jwk.toJSONString();
        } catch (Exception e) {
            log.warn("Failed to serialize public key as JWK, falling back to encoded form: {}", e.getMessage());
            return java.util.Base64.getEncoder().encodeToString(verification.publicKey().getEncoded());
        }
    }

    private static String checkHintLength(String field, String value) {
        if (value != null && value.length() > MAX_HINT_LENGTH) {
            return field + " exceeds maximum length of " + MAX_HINT_LENGTH + " characters";
        }
        return null;
    }

    private boolean isEmptyBody(RoutingContext ctx) {
        if (ctx.body() == null) return true;
        var buffer = ctx.body().buffer();
        return buffer == null || buffer.length() == 0;
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
