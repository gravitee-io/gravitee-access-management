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
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentProcessHandler implements Handler<RoutingContext> {
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String REQUESTED_CONSENT_CONTEXT_KEY = "requestedConsent";
    private static final String USER_CONSENT_COMPLETED_CONTEXT_KEY = "userConsentCompleted";
    private static final String USER_OAUTH_APPROVAL = "user_oauth_approval";
    private static final String SCOPE_PREFIX = "scope.";
    private Domain domain;
    private UserConsentService userConsentService;

    public UserConsentProcessHandler(UserConsentService userConsentService, Domain domain) {
        this.userConsentService = userConsentService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Session session = routingContext.session();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final Set<String> requestedConsent = session.get(REQUESTED_CONSENT_CONTEXT_KEY);
        final AuthorizationRequest authorizationRequest = session.get(OAuth2Constants.AUTHORIZATION_REQUEST);

        // get user consent
        MultiMap params = routingContext.request().formAttributes();
        Map<String, String> userConsent = params.entries().stream()
                .filter(entry -> entry.getKey().startsWith(SCOPE_PREFIX))
                .collect(Collectors.toMap(scopeEntry -> scopeEntry.getKey(), scopeEntry -> params.get(USER_OAUTH_APPROVAL)));

        // compute user consent that have been approved / denied
        Set<String> approvedConsent = new HashSet<>();
        List<ScopeApproval> approvals = new ArrayList<>();
        for (String requestedScope : requestedConsent) {
            String approvalParameter = requestedScope;
            String value = userConsent.get(SCOPE_PREFIX + approvalParameter);
            value = value == null ? "" : value.toLowerCase();
            if ("true".equals(value) || value.startsWith("approve")) {
                approvedConsent.add(requestedScope);
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getId(), client.getClientId(), domain.getId(),
                        requestedScope, ScopeApproval.ApprovalStatus.APPROVED));
            }
            else {
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getId(), client.getClientId(), domain.getId(),
                        requestedScope, ScopeApproval.ApprovalStatus.DENIED));
            }
        }

        // save consent
        saveConsent(request, user, client, approvals, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }

            boolean approved = (approvedConsent.isEmpty() && !requestedConsent.isEmpty()) ? false : true;
            authorizationRequest.setApproved(approved);
            authorizationRequest.setScopes(approvedConsent);
            authorizationRequest.setConsents(h.result());
            session.put(USER_CONSENT_COMPLETED_CONTEXT_KEY, true);
            routingContext.next();
        });
    }

    private void saveConsent(HttpServerRequest request, io.gravitee.am.model.User endUser, Client client, List<ScopeApproval> approvals, Handler<AsyncResult<List<ScopeApproval>>> handler) {
        userConsentService.saveConsent(client, approvals, getAuthenticatedUser(request, endUser))
                .subscribe(
                        approvals1 -> handler.handle(Future.succeededFuture(approvals1)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private io.gravitee.am.identityprovider.api.User getAuthenticatedUser(HttpServerRequest request, io.gravitee.am.model.User user) {
        io.gravitee.am.identityprovider.api.User authenticatedUser = new DefaultUser(user.getUsername());
        ((DefaultUser) authenticatedUser).setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        // add ip address and user agent
        additionalInformation.put(Claims.ip_address, remoteAddress(request));
        additionalInformation.put(Claims.user_agent, userAgent(request));
        additionalInformation.put(Claims.domain, domain.getId());
        ((DefaultUser) authenticatedUser).setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }

    public String remoteAddress(HttpServerRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(io.gravitee.common.http.HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            SocketAddress address = httpServerRequest.remoteAddress();
            remoteAddress = (address != null) ? address.host() : null;
        }

        return remoteAddress;
    }

    private String userAgent(HttpServerRequest request) {
        return request.getHeader(io.gravitee.common.http.HttpHeaders.USER_AGENT);
    }
}
