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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenClaims;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.aauth.util.AAuthKeyUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * POST /aauth/consent — processes the consent form submission.
 * Creates ScopeApproval records, issues the auth token, and marks the pending request as completed.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthConsentPostEndpoint implements Handler<RoutingContext> {

    private static final String SCOPE_PREFIX = "scope.";
    private static final String USER_APPROVAL_PARAM = "user_oauth_approval";

    private final AAuthPendingRequestService pendingService;
    private final AAuthTokenService tokenService;
    private final AAuthConsentService consentService;
    private final ApplicationService applicationService;
    private final Domain domain;

    @Override
    public void handle(RoutingContext ctx) {
        MultiMap params = ctx.request().formAttributes();
        String code = params.get("code");

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

        boolean approved = "true".equalsIgnoreCase(params.get(USER_APPROVAL_PARAM));
        String clarificationQuestion = params.get("clarification_question");
        boolean isClarification = clarificationQuestion != null && !clarificationQuestion.isBlank();

        // Resolve pending request and Application from DB (not session)
        pendingService.findByInteractionCode(code)
                .switchIfEmpty(Single.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                .flatMap(pending -> resolveApplication(pending)
                        .map(client -> new ResolvedContext(pending, client)))
                .flatMapCompletable(resolved -> {
                    if (isClarification) {
                        return clarifyFlow(ctx, resolved.pending, clarificationQuestion, code);
                    } else if (approved) {
                        return approveFlow(ctx, resolved.pending, resolved.client, user, params);
                    } else {
                        return denyFlow(ctx, resolved.pending);
                    }
                })
                .subscribe(
                        () -> { /* response already sent */ },
                        err -> {
                            log.error("Error processing consent: {}", err.getMessage());
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

    private Completable approveFlow(RoutingContext ctx, AAuthPendingRequest pending,
                                     Client client, io.gravitee.am.model.User user, MultiMap params) {
        Set<String> approvedScopes = extractApprovedScopes(params);
        io.gravitee.am.identityprovider.api.User principal = getAuthenticatedUser(ctx, user);

        Single<List<ScopeApproval>> saveConsent = approvedScopes.isEmpty()
                ? Single.just(List.of())
                : consentService.saveConsent(client, user.getFullId(), approvedScopes, principal);

        return saveConsent
                .flatMap(saved -> createAuthTokenAndApprove(pending, user.getId()))
                .doOnSuccess(approved -> ctx.response()
                        .putHeader("Content-Type", "text/html")
                        .end("<html><body>" +
                                "<h2>Authorization Granted</h2>" +
                                "<p>You have authorized the agent. You can close this window.</p>" +
                                "</body></html>"))
                .ignoreElement();
    }

    private Completable clarifyFlow(RoutingContext ctx, AAuthPendingRequest pending,
                                     String question, String code) {
        return pendingService.askClarification(pending.getId(), question)
                .ignoreElement()
                .doOnComplete(() -> {
                    // Redirect back to GET /consent so the page shows "waiting for agent"
                    String consentUrl = resolveConsentUrl(ctx) + "?code=" + code;
                    ctx.response()
                            .putHeader("Location", consentUrl)
                            .setStatusCode(302)
                            .end();
                });
    }

    private String resolveConsentUrl(RoutingContext ctx) {
        try {
            return io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest
                    .resolveProxyRequest(ctx.request(),
                            ctx.get(io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH))
                    + "/aauth/consent";
        } catch (Exception e) {
            return "/aauth/consent";
        }
    }

    private Completable denyFlow(RoutingContext ctx, AAuthPendingRequest pending) {
        return pendingService.deny(pending.getId())
                .doOnSuccess(denied -> ctx.response()
                        .putHeader("Content-Type", "text/html")
                        .end("<html><body>" +
                                "<h2>Authorization Denied</h2>" +
                                "<p>You have denied the agent's request. You can close this window.</p>" +
                                "</body></html>"))
                .ignoreElement();
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
                    .flatMap(response -> pendingService.approve(
                            pending.getId(), response.authToken(), response.expiresIn(), userId));
        } catch (InvalidKeySpecException e) {
            return Single.error(e);
        }
    }

    private Set<String> extractApprovedScopes(MultiMap params) {
        return params.entries().stream()
                .filter(e -> e.getKey().startsWith(SCOPE_PREFIX))
                .map(e -> e.getKey().substring(SCOPE_PREFIX.length()))
                .collect(Collectors.toSet());
    }

    private io.gravitee.am.identityprovider.api.User getAuthenticatedUser(
            RoutingContext context, io.gravitee.am.model.User user) {
        DefaultUser authenticatedUser = new DefaultUser(user.getUsername());
        authenticatedUser.setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        if (canSaveIp(context)) {
            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(context.request()));
        }
        if (canSaveUserAgent(context)) {
            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(context.request()));
        }
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        authenticatedUser.setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }

    private record ResolvedContext(AAuthPendingRequest pending, Client client) {}
}
