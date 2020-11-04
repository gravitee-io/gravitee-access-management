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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.JWTOAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.jwt.JWTAuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.
 * The authorization server MUST first verify the identity of the resource owner.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1">3.1. Authorization Endpoint</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String USER_CONSENT_COMPLETED_CONTEXT_KEY = "userConsentCompleted";
    private static final String REQUESTED_CONSENT_CONTEXT_KEY = "requestedConsent";
    private static final String WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY = "webAuthnCredentialId";
    private static final String MFA_FACTOR_ID_CONTEXT_KEY = "mfaFactorId";
    private Flow flow;

    public AuthorizationEndpoint(Flow flow) {
        this.flow = flow;
    }

    @Override
    public void handle(RoutingContext context) {
        // The authorization server authenticates the resource owner and obtains
        // an authorization decision (by asking the resource owner or by establishing approval via other means).
        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            throw new AccessDeniedException();
        }

        // get authorization request
        AuthorizationRequest request = context.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);

        // get client
        Client client = context.get(CLIENT_CONTEXT_KEY);

        // get resource owner
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        flow.run(request, client, endUser)
                .subscribe(
                        authorizationResponse -> {
                            try {
                                // final step of the authorization flow, we can clean the session and redirect the user
                                cleanSession(context);
                                doRedirect(context.response(), authorizationResponse.buildRedirectUri());
                            } catch (Exception e) {
                                logger.error("Unable to redirect to client redirect_uri", e);
                                context.fail(new ServerErrorException());
                            }
                        },
                        error -> context.fail(error));

    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private void cleanSession(RoutingContext context) {
        context.session().remove(OAuth2Constants.AUTHORIZATION_REQUEST);
        context.session().remove(USER_CONSENT_COMPLETED_CONTEXT_KEY);
        context.session().remove(REQUESTED_CONSENT_CONTEXT_KEY);
        context.session().remove(WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        context.session().remove(MFA_FACTOR_ID_CONTEXT_KEY);
    }
}
