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
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

/**
 * After an approved scope submission, makes the authorization detail consent screen the next step when the request
 * has details the user still has to consent to.
 */
public class AuthorizationDetailsConsentPendingHandler implements Handler<RoutingContext> {

    private final AuthorizationDetailsConsentService authorizationDetailsConsentService;

    public AuthorizationDetailsConsentPendingHandler(AuthorizationDetailsConsentService authorizationDetailsConsentService) {
        this.authorizationDetailsConsentService = authorizationDetailsConsentService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        final AuthorizationDetails requested = authorizationRequest.getAuthorizationDetails();
        if (ConsentStage.of(routingContext) != ConsentStage.SCOPES
                || !Boolean.TRUE.equals(routingContext.session().get(ConstantKeys.USER_CONSENT_APPROVED_KEY))
                || requested == null || requested.isEmpty()) {
            routingContext.next();
            return;
        }

        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final boolean promptConsent = authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains("consent");
        authorizationDetailsConsentService.getDetailsToPresent(client, user, requested, promptConsent)
                .subscribe(toPresent -> {
                    if (!toPresent.isEmpty()) {
                        routingContext.session().put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);
                        routingContext.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);
                    }
                    routingContext.next();
                }, routingContext::fail);
    }
}
