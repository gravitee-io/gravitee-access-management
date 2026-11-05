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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InteractionRequiredException;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * Obtains the user's consent to the authorization details (RFC 9396) of the request. A rejection, or details other
 * than those the user decided on, fail with {@code access_denied}.
 */
public class AuthorizationRequestAuthorizationDetailsConsentHandler implements Handler<RoutingContext> {

    private final AuthorizationDetailsConsentService authorizationDetailsConsentService;

    public AuthorizationRequestAuthorizationDetailsConsentHandler(AuthorizationDetailsConsentService authorizationDetailsConsentService) {
        this.authorizationDetailsConsentService = authorizationDetailsConsentService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final AuthorizationRequest authorizationRequest = routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);
        final AuthorizationDetails requested = authorizationRequest.getAuthorizationDetails();
        if (requested == null || requested.isEmpty()) {
            routingContext.next();
            return;
        }

        final Session session = routingContext.session();
        final String decidedDigest = session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY);
        if (decidedDigest != null) {
            if (Boolean.TRUE.equals(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY))
                    && decidedDigest.equals(AuthorizationDetailDigest.ofAll(requested.getAll()))) {
                routingContext.next();
            } else {
                routingContext.fail(new AccessDeniedException("User denied access"));
            }
            return;
        }

        if (routingContext.user() == null) {
            routingContext.fail(new AccessDeniedException("User denied access"));
            return;
        }
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final boolean promptConsent = hasPrompt(authorizationRequest, "consent");
        authorizationDetailsConsentService.getDetailsToPresent(client, user, requested, promptConsent)
                .subscribe(toPresent -> {
                    if (toPresent.isEmpty()) {
                        routingContext.next();
                        return;
                    }
                    // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
                    if (hasPrompt(authorizationRequest, "none")) {
                        routingContext.fail(new InteractionRequiredException("Interaction required"));
                        return;
                    }
                    session.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);
                    AuthorizationRequestEndUserConsentHandler.redirectToConsentPage(routingContext);
                }, routingContext::fail);
    }

    private static boolean hasPrompt(AuthorizationRequest authorizationRequest, String prompt) {
        return authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains(prompt);
    }
}
