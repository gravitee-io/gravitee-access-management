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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenClaims;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.util.AAuthKeyUtils;
import io.gravitee.am.gateway.handler.aauth.util.MarkdownSanitizer;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Handles GET /aauth/consent — resolves the pending request by interaction code,
 * checks consent cache, and either approves from cache or renders the consent page.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthConsentHandler implements Handler<RoutingContext> {

    private final AAuthConsentService consentService;
    private final AAuthPendingRequestService pendingRequestService;
    private final AAuthTokenService tokenService;
    private final ApplicationService applicationService;
    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    @Override
    public void handle(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        if (code == null || code.isBlank()) {
            ctx.fail(new InvalidRequestException("Missing interaction code"));
            return;
        }

        io.gravitee.am.model.User user = ctx.user() != null
                ? ((User) ctx.user().getDelegate()).getUser()
                : null;

        if (user == null) {
            ctx.fail(new HttpException(401, "Authentication required"));
            return;
        }

        // Resolve → load app → mark interacting → check consent → render or approve
        pendingRequestService.findByInteractionCode(code)
                .switchIfEmpty(Single.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                // Resolve the Application from the pending request
                .flatMap(pending -> resolveApplication(pending)
                        .map(client -> new ResolvedContext(pending, client)))
                // Handle cancelled/awaiting states before proceeding to consent
                .flatMap(resolved -> {
                    String status = resolved.pending.getStatus();
                    if (io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus.CANCELLED.name().equals(status)) {
                        return Single.error(new HttpException(410, "The agent has cancelled this request"));
                    }
                    if (io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus.AWAITING_CLARIFICATION.name().equals(status)) {
                        // Skip markInteracting — render the waiting page with the current state
                        Set<String> requestedScopes = parseScopes(resolved.pending.getScope());
                        return Single.just(new ConsentDecision(false, resolved.pending, resolved.client, requestedScopes));
                    }
                    // Normal flow: mark as INTERACTING, then check consent cache
                    return pendingRequestService.markInteracting(resolved.pending.getId(), user.getId())
                        .andThen(Single.defer(() -> {
                            resolved.pending.setUserId(user.getId());
                            Set<String> requestedScopes = parseScopes(resolved.pending.getScope());

                            return consentService.checkConsent(user.getFullId(), resolved.client.getClientId())
                                    .map(approvedScopes -> {
                                        if (approvedScopes.containsAll(requestedScopes)) {
                                            return new ConsentDecision(true, resolved.pending, resolved.client, requestedScopes);
                                        }
                                        Set<String> missing = new HashSet<>(requestedScopes);
                                        missing.removeAll(approvedScopes);
                                        return new ConsentDecision(false, resolved.pending, resolved.client, missing);
                                    });
                        }));
                })
                // Produce the HTTP response: approve from cache or render consent page
                .flatMapCompletable(decision -> processDecision(ctx, decision, user))
                .subscribe(
                        () -> { /* response already sent */ },
                        err -> {
                            log.error("Error in consent flow: {}", err.getMessage());
                            ctx.fail(err);
                        }
                );
    }

    private Single<Client> resolveApplication(AAuthPendingRequest pending) {
        if (pending.getApplicationId() == null) {
            return Single.error(new InvalidRequestException("Agent was not identified"));
        }
        return applicationService.findById(pending.getApplicationId())
                .switchIfEmpty(Single.error(() -> new InvalidRequestException(
                        "No client found for client_id " + pending.getApplicationId())))
                .map(Application::toClient);
    }

    private Completable processDecision(RoutingContext ctx, ConsentDecision decision, io.gravitee.am.model.User user) {

        if (decision.consentGiven) {
            return createAuthTokenAndApprove(decision.pending, user.getId())
                    .flatMapCompletable(aAuthPendingRequest -> renderApprovedPage(ctx));
        }

        return renderConsentPage(ctx, decision.pending, decision.client, decision.scopes);
    }

    private Single<AAuthPendingRequest> createAuthTokenAndApprove(
            AAuthPendingRequest pending, String userId) {
        ResourceTokenClaims rtClaims = new ResourceTokenClaims(
                pending.getResourceIss(), null, null,
                pending.getAgentSub(), null, pending.getScope(), 0, 0);

        try {
            PublicKey agentKey = AAuthKeyUtils.deserializePublicKey(pending.getAgentPublicKey());
            VerificationResult verification = new VerificationResult(
                    "jwt", "sig", agentKey, pending.getAgentJkt(), pending.getAgentId(), pending.getAgentSub());

            return tokenService.createAuthToken(rtClaims, verification, pending.getPsIssuerUrl(), userId)
                    .flatMap(response -> pendingRequestService.approve(
                            pending.getId(), response.authToken(), response.expiresIn(), userId));
        } catch (InvalidKeySpecException e) {
            return Single.error(e);
        }
    }

    private Completable renderApprovedPage(RoutingContext ctx) {
        return ctx.response()
                .putHeader("Content-Type", "text/html")
                .end("<html><body>" +
                        "<h2>Authorization Granted</h2>" +
                        "<p>You have authorized the agent. You can close this window.</p>" +
                        "</body></html>");
    }

    private Completable renderConsentPage(RoutingContext ctx, AAuthPendingRequest pending,
                                          Client client, Set<String> scopesToApprove) {
        final String action = resolveConsentUrl(ctx);

        var scopes = scopesToApprove.stream()
                .map(Scope::new)
                .toList();

        ctx.put(ConstantKeys.SCOPES_CONTEXT_KEY, scopes);
        ctx.put(ConstantKeys.ACTION_KEY, action);
        ctx.put("justification", MarkdownSanitizer.toSafeHtml(pending.getJustification()));
        ctx.put("interactionCode", pending.getInteractionCode());
        ctx.put("agentName", client.getClientName() != null ? client.getClientName() : pending.getAgentId());
        ctx.put("agentLogoUri", client.getLogoUri());
        ctx.put("clarificationSupported", pending.isClarificationSupported());
        ctx.put("clarificationResponse", MarkdownSanitizer.toSafeHtml(pending.getClarificationResponse()));
        ctx.put("clarificationRoundCount", pending.getClarificationRoundCount());
        ctx.put("maxClarificationRounds", 5);
        ctx.put("awaitingClarification",
                io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus.AWAITING_CLARIFICATION.name()
                        .equals(pending.getStatus()));

        String templateName = Template.AAUTH_CONSENT.template()
                + (client.getId() != null ? "|" + client.getId() : "");

        return engine.render(generateData(ctx, domain, client), templateName)
                .flatMapCompletable(buffer -> ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                        .end(buffer));
    }

    private String resolveConsentUrl(RoutingContext ctx) {
        try {
            return UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH))
                    + "/aauth/consent";
        } catch (Exception e) {
            return "/aauth/consent";
        }
    }

    private Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(scope.split("\\s+")));
    }

    private record ResolvedContext(AAuthPendingRequest pending, Client client) {
    }

    private record ConsentDecision(boolean consentGiven, AAuthPendingRequest pending, Client client,
                                   Set<String> scopes) {
    }
}
