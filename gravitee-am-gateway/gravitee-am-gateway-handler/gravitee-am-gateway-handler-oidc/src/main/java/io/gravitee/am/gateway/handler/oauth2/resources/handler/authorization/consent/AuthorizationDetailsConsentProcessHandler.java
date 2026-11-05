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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import static io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants.USER_OAUTH_APPROVAL;

/**
 * A submission for details other than those presented counts as a rejection. The decision is enforced when the
 * authorization request is replayed.
 */
public class AuthorizationDetailsConsentProcessHandler implements Handler<RoutingContext> {

    private final AuthorizationDetailsConsentService authorizationDetailsConsentService;
    private final Domain domain;

    public AuthorizationDetailsConsentProcessHandler(AuthorizationDetailsConsentService authorizationDetailsConsentService, Domain domain) {
        this.authorizationDetailsConsentService = authorizationDetailsConsentService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        final AuthorizationDetails requested = authorizationRequest.getAuthorizationDetails();
        final String presentedDigest = session.remove(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY);
        session.remove(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY);
        if (requested == null || requested.isEmpty()) {
            routingContext.next();
            return;
        }

        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final boolean userApproved = "true".equalsIgnoreCase(routingContext.request().formAttributes().get(USER_OAUTH_APPROVAL));
        final boolean promptConsent = authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains("consent");
        authorizationDetailsConsentService.getDetailsToPresent(client, user, requested, promptConsent)
                .flatMapCompletable(presented -> {
                    final boolean approved = userApproved && AuthorizationDetailDigest.ofAll(presented).equals(presentedDigest);
                    return authorizationDetailsConsentService.saveConsent(client, user, ConsentPrincipal.of(routingContext, user, domain),
                                    requested, presented, approved)
                            .doOnComplete(() -> {
                                session.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY, AuthorizationDetailDigest.ofAll(requested.getAll()));
                                session.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY, approved);
                            });
                })
                .subscribe(routingContext::next, routingContext::fail);
    }
}
