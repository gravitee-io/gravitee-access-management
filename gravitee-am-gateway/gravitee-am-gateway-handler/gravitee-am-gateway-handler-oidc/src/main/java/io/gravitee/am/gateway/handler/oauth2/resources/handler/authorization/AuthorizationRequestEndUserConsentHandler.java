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

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InteractionRequiredException;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Once the End-User is authenticated, the Authorization Server MUST obtain an authorization decision before releasing information to the Relying Party.
 * When permitted by the request parameters used, this MAY be done through an interactive dialogue with the End-User that makes it clear what is being consented to or by establishing consent via conditions for processing the request or other means (for example, via previous administrative consent).
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#Consent">3.1.2.4.  Authorization Server Obtains End-User Consent/Authorization</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestEndUserConsentHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationRequestEndUserConsentHandler.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String USER_CONSENT_COMPLETED_CONTEXT_KEY = "userConsentCompleted";
    private static final String REQUESTED_CONSENT_CONTEXT_KEY = "requestedConsent";
    private UserConsentService userConsentService;
    private String redirectURL;

    public AuthorizationRequestEndUserConsentHandler(UserConsentService userConsentService, Domain domain) {
        this.userConsentService = userConsentService;
        this.redirectURL = "/" + domain.getPath() + "/oauth/consent";
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final HttpServerRequest request = routingContext.request();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = routingContext.user() != null ? ((User) routingContext.user().getDelegate()).getUser() : null;
        final AuthorizationRequest authorizationRequest = session.get(OAuth2Constants.AUTHORIZATION_REQUEST);
        final Set<String> requestedConsent = authorizationRequest.getScopes();
        // no consent to check, continue
        if (requestedConsent == null || requestedConsent.isEmpty()) {
            routingContext.next();
            return;
        }
        // check if user is already set its consent
        if (session.get(USER_CONSENT_COMPLETED_CONTEXT_KEY) != null && session.get(USER_CONSENT_COMPLETED_CONTEXT_KEY).equals(true)) {
            if (authorizationRequest.isApproved()) {
                routingContext.next();
                return;
            }
            // if prompt=none and the Client does not have pre-configured consent for the requested Claims, throw interaction_required exception
            // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
            String prompt = request.params().get(Parameters.PROMPT);
            if (prompt != null && Arrays.asList(prompt.split("\\s+")).contains("none")) {
                routingContext.fail(new InteractionRequiredException("Interaction required"));
            } else {
                routingContext.fail(new AccessDeniedException("User denied access"));
            }
            return;
        }
        // application has forced to prompt consent screen to the user
        // go to the user consent page
        if (request.params().contains(Parameters.PROMPT)
                && request.params().get(Parameters.PROMPT).contains("consent")) {
            session.put(REQUESTED_CONSENT_CONTEXT_KEY, requestedConsent);
            redirectToConsentPage(request);
            return;
        }
        // check if application has enabled skip consent option
        if (skipConsent(requestedConsent, client)) {
            authorizationRequest.setApproved(true);
            routingContext.next();
            return;
        }
        // check user consent
        checkUserConsent(client, user, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }
            Set<String> approvedConsent = h.result();
            // user approved consent, continue
            if (approvedConsent.containsAll(requestedConsent)) {
                authorizationRequest.setApproved(true);
                routingContext.next();
                return;
            }
            // else go to the user consent page
            Set<String> requiredConsent = requestedConsent.stream().filter(requestedScope -> !approvedConsent.contains(requestedScope)).collect(Collectors.toSet());
            session.put(REQUESTED_CONSENT_CONTEXT_KEY, requiredConsent);
            redirectToConsentPage(request);
        });
    }

    private void checkUserConsent(Client client, io.gravitee.am.model.User user, Handler<AsyncResult<Set<String>>> handler) {
        userConsentService.checkConsent(client, user)
                .subscribe(
                        result -> handler.handle(Future.succeededFuture(result)),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private boolean skipConsent(Set<String> requestedConsent, Client client) {
        List<String> clientAutoApproveScopes = client.getAutoApproveScopes();
        Set<String> approvedScopes = requestedConsent.stream().filter(s -> isAutoApprove(clientAutoApproveScopes, s)).collect(Collectors.toSet());
        return approvedScopes.containsAll(requestedConsent);
    }

    private boolean isAutoApprove(List<String> autoApproveScopes, String scope) {
        if (autoApproveScopes == null) {
            return false;
        }
        for (String auto : autoApproveScopes) {
            if (auto.equals("true") || scope.matches(auto)) {
                return true;
            }
        }
        return false;
    }

    public void redirectToConsentPage(HttpServerRequest request) {
        try {
            final Map<String, String> requestParameters = request.params().entries().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String proxiedRedirectURI = UriBuilderRequest.resolveProxyRequest(request, redirectURL, requestParameters, true);
            request.response()
                    .putHeader(HttpHeaders.LOCATION, proxiedRedirectURI)
                    .setStatusCode(302)
                    .end();
        } catch (Exception e) {
            LOGGER.warn("Failed to decode consent redirect url", e);
            request.response()
                    .putHeader(HttpHeaders.LOCATION, redirectURL)
                    .setStatusCode(302)
                    .end();
        }
    }
}
