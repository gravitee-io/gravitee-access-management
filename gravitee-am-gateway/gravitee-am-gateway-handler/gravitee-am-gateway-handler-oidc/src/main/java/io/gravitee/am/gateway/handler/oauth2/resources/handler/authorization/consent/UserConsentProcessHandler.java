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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants.SCOPE_PREFIX;
import static io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants.USER_OAUTH_APPROVAL;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentProcessHandler implements Handler<RoutingContext> {

    private final Domain domain;
    private final UserConsentService userConsentService;

    public UserConsentProcessHandler(UserConsentService userConsentService, Domain domain) {
        this.userConsentService = userConsentService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);

        final Set<String> requestedConsent = authorizationRequest.getScopes() == null
                ? new HashSet<>() : new HashSet<>(authorizationRequest.getScopes());

        final boolean prompt = authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains("consent");
        if (prompt || requestedConsent.isEmpty()) {
            processConsent(routingContext, client, user, authorizationRequest, requestedConsent, requestedConsent);
            return;
        }

        // reconstruct the "presented" subset to avoid touching previously approved scopes
        userConsentService.checkConsent(client, user)
                .subscribe(
                        alreadyApproved -> {
                            Set<String> presentedConsent = requestedConsent.stream()
                                    .filter(scope -> !alreadyApproved.contains(scope))
                                    .collect(Collectors.toSet());
                            processConsent(routingContext, client, user, authorizationRequest, requestedConsent, presentedConsent);
                        },
                        error -> routingContext.fail(error));
    }

    private void processConsent(RoutingContext routingContext,
                                Client client,
                                io.gravitee.am.model.User user,
                                AuthorizationRequest authorizationRequest,
                                Set<String> requestedConsent,
                                Set<String> presentedConsent) {
        final HttpServerRequest request = routingContext.request();
        final Session session = routingContext.session();
        final MultiMap params = request.formAttributes();
        final boolean userRejected = "false".equalsIgnoreCase(params.get(USER_OAUTH_APPROVAL));

        // derive each scope's outcome from its own submitted field value
        Set<String> approvedConsent = new HashSet<>();
        List<ScopeApproval> approvals = new ArrayList<>();
        for (String presentedScope : presentedConsent) {
            String value = userRejected ? "false" : params.get(SCOPE_PREFIX + presentedScope);
            value = value == null ? "" : value.toLowerCase();
            if ("true".equals(value) || value.startsWith("approve")) {
                approvedConsent.add(presentedScope);
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getFullId(), client.getClientId(), domain.getId(),
                        presentedScope, ScopeApproval.ApprovalStatus.APPROVED));
            } else {
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getFullId(), client.getClientId(), domain.getId(),
                        presentedScope, ScopeApproval.ApprovalStatus.DENIED));
            }
        }

        // save consent
        saveConsent(routingContext, request, user, client, approvals, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }

            boolean approved = !approvedConsent.isEmpty() || requestedConsent.isEmpty();
            authorizationRequest.setApproved(approved);
            authorizationRequest.setScopes(approvedConsent);
            authorizationRequest.setConsents(h.result());
            session.put(ConstantKeys.USER_CONSENT_COMPLETED_KEY, true);
            session.put(ConstantKeys.USER_CONSENT_APPROVED_KEY, approved);
            routingContext.next();
        });
    }

    private void saveConsent(RoutingContext context,
                             HttpServerRequest request,
                             io.gravitee.am.model.User endUser,
                             Client client,
                             List<ScopeApproval> approvals, Handler<AsyncResult<List<ScopeApproval>>> handler) {
        userConsentService.saveConsent(client, approvals, getAuthenticatedUser(context, request, endUser))
                .subscribe(
                        approvals1 -> handler.handle(Future.succeededFuture(approvals1)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private io.gravitee.am.identityprovider.api.User getAuthenticatedUser(RoutingContext context,
                                                                          HttpServerRequest request,
                                                                          io.gravitee.am.model.User user) {
        DefaultUser authenticatedUser = new DefaultUser(user.getUsername());
        authenticatedUser.setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        // add ip address and user agent
        if (canSaveIp(context)) {
            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(request));
        }
        if (canSaveUserAgent(context)) {
            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(request));
        }
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        authenticatedUser.setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }
}
