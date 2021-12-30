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
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants.SCOPE_PREFIX;
import static io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants.USER_OAUTH_APPROVAL;

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
                approvals.add(new ScopeApproval(authorizationRequest.transactionId(), user.getId(), client.getClientId(), domain.getId(),
                        requestedScope, ScopeApproval.ApprovalStatus.APPROVED));
            } else {
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

            boolean approved = !approvedConsent.isEmpty() || requestedConsent.isEmpty();
            authorizationRequest.setApproved(approved);
            authorizationRequest.setScopes(approvedConsent);
            authorizationRequest.setConsents(h.result());
            session.put(ConstantKeys.USER_CONSENT_COMPLETED_KEY, true);
            session.put(ConstantKeys.USER_CONSENT_APPROVED_KEY, approved);
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
        DefaultUser authenticatedUser = new DefaultUser(user.getUsername());
        authenticatedUser.setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        // add ip address and user agent
        additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(request));
        additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(request));
        additionalInformation.put(Claims.domain, domain.getId());
        authenticatedUser.setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }
}
