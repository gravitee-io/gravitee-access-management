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
import io.gravitee.am.gateway.handler.common.session.SessionManager;
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
import io.vertx.rxjava3.core.MultiMap;
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
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentProcessHandler implements Handler<RoutingContext> {

    private final Domain domain;
    private final UserConsentService userConsentService;
    private final SessionManager sessionManager;
    public UserConsentProcessHandler(UserConsentService userConsentService, Domain domain) {
        this.sessionManager = new SessionManager();
        this.userConsentService = userConsentService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Session session = routingContext.session();
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);

        // get user consent
        MultiMap params = routingContext.request().formAttributes();
        Map<String, String> userConsent = params.entries().stream()
                .filter(entry -> entry.getKey().startsWith(SCOPE_PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, scopeEntry -> params.get(USER_OAUTH_APPROVAL)));

        final Set<String> requestedConsent = userConsent.keySet().stream()
                .map(requestedScope -> requestedScope.replace(SCOPE_PREFIX, ""))
                .collect(Collectors.toSet());

        // compute user consent that have been approved / denied
        Set<String> approvedConsent = new HashSet<>();
        List<ScopeApproval> approvals = new ArrayList<>();
        for (String requestedScope : requestedConsent) {
            String value = userConsent.get(SCOPE_PREFIX + requestedScope);
            value = value == null ? "" : value.toLowerCase();
            if ("true".equals(value) || value.startsWith("approve")) {
                approvedConsent.add(requestedScope);
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getFullId(), client.getClientId(), domain.getId(),
                        requestedScope, ScopeApproval.ApprovalStatus.APPROVED));
            } else {
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getFullId(), client.getClientId(), domain.getId(),
                        requestedScope, ScopeApproval.ApprovalStatus.DENIED));
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
            final var state = sessionManager.getSessionState(routingContext);
            final var consentState = state.getConsentState();
            consentState.consentComplete();
            if (approved) {
                consentState.approve();
            }
            state.save(session);

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
