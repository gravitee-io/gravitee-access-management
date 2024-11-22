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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import com.google.common.net.HttpHeaders;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_ISSUING_REASON;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_PROVIDER_ID;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_STATUS;
import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_TARGET;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ISSUING_REASON_CLOSE_IDP_SESSION;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.STATUS_SIGNED_IN;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.gateway.handler.common.vertx.utils.RedirectHelper.getReturnUrl;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.LOGGER;
import static io.gravitee.am.gateway.handler.root.RootProvider.PATH_LOGIN_CALLBACK;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public LoginCallbackEndpoint(JWTService jwtService,
                                 CertificateManager certificateManager) {
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final String returnURL = getReturnUrl(routingContext, routingContext.get(PARAM_CONTEXT_KEY));

        // if we have an id_token, put in the session context for post step (mainly the user consent step)
        if (session != null) {
            if (routingContext.data().containsKey(ID_TOKEN_KEY)) {
                session.put(ID_TOKEN_KEY, routingContext.get(ID_TOKEN_KEY));
            }
            // save that the user has just been signed in
            session.put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);

            final var sessionState = sessionManager.getSessionState(routingContext);
            sessionState.getUserAuthState().isSignedIn();
            sessionState.save(session);
        }

        AuthenticationProvider authProvider = routingContext.get(ConstantKeys.PROVIDER_CONTEXT_KEY);
        // the login process is done, so we want to close the session after the authentication
        if (authProvider instanceof SocialAuthenticationProvider socialIdp && socialIdp.closeSessionAfterSignIn() == CloseSessionMode.REDIRECT) {
            final User endUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY) != null ?
                    routingContext.get(ConstantKeys.USER_CONTEXT_KEY) :
                    (routingContext.user() != null ? ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null);
            SimpleAuthenticationContext simpleAuthenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(routingContext.request().getDelegate()));
            final Authentication authentication = new EndUserAuthentication(endUser, null, simpleAuthenticationContext);
            final var logoutRequest = socialIdp.signOutUrl(authentication);

            final var stateJwt = new JWT();

            stateJwt.put(CLAIM_TARGET, returnURL);
            stateJwt.put(CLAIM_PROVIDER_ID, routingContext.get(ConstantKeys.PROVIDER_ID_PARAM_KEY));
            stateJwt.put(CLAIM_ISSUING_REASON, ISSUING_REASON_CLOSE_IDP_SESSION);
            stateJwt.put(CLAIM_STATUS, STATUS_SIGNED_IN);

            jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider())
                    .flatMapMaybe(state ->
                            logoutRequest.map(req -> {
                                var callbackUri = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + PATH_LOGIN_CALLBACK);
                                return UriBuilder.fromHttpUrl(req.getUri())
                                        .addParameter(io.gravitee.am.common.oidc.Parameters.ID_TOKEN_HINT, encodeURIComponent(routingContext.get(OIDC_PROVIDER_ID_TOKEN_KEY)))
                                        .addParameter(Parameters.STATE, encodeURIComponent(state))
                                        .addParameter(io.gravitee.am.common.oidc.Parameters.POST_LOGOUT_REDIRECT_URI, encodeURIComponent(callbackUri))
                                        .buildString();
                            })
                    )
                    // if the Maybe is empty, we redirect the user to the original request
                    .switchIfEmpty(Maybe.just(returnURL))
                    .subscribe(
                            url -> {
                                LOGGER.debug("Call logout on provider '{}'", (String) routingContext.get(ConstantKeys.PROVIDER_ID_PARAM_KEY));
                                doRedirect(routingContext.response(), url);
                            },
                            err -> {
                                LOGGER.error("Session can't be closed on provider '{}'", routingContext.get(ConstantKeys.PROVIDER_ID_PARAM_KEY), err);
                                doRedirect(routingContext.response(), returnURL);
                            });
        } else {
            // the login process is done
            // redirect the user to the original request
            doRedirect(routingContext.response(), returnURL);
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
