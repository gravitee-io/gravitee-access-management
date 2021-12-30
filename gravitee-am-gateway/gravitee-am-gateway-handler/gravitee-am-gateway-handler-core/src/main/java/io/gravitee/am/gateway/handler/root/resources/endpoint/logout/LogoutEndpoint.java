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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpoint extends AbstractLogoutEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(LogoutEndpoint.class);
    private IdentityProviderManager identityProviderManager;
    private CertificateManager certificateManager;
    private ClientSyncService clientSyncService;
    private JWTService jwtService;
    private WebClient webClient;

    public LogoutEndpoint(Domain domain,
                          ClientSyncService clientSyncService,
                          JWTService jwtService,
                          UserService userService,
                          AuthenticationFlowContextService authenticationFlowContextService,
                          IdentityProviderManager identityProviderManager,
                          CertificateManager certificateManager,
                          WebClient webClient) {
        super(domain, userService, authenticationFlowContextService);
        this.jwtService = jwtService;
        this.clientSyncService = clientSyncService;
        this.certificateManager = certificateManager;
        this.identityProviderManager = identityProviderManager;
        this.webClient = webClient;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
            case "POST":
                logout(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void logout(RoutingContext routingContext) {
        // restore the current session
        restoreCurrentSession(routingContext, sessionHandler -> {
            final UserToken currentSession = sessionHandler.result();

            if (currentSession != null) {
                // put current session in context for later use
                if (currentSession.getClient() != null) {
                    Client safeClient = new Client(currentSession.getClient());
                    safeClient.setClientSecret(null);
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, safeClient);
                }
                if (currentSession.getUser() != null) {
                    routingContext.put(ConstantKeys.USER_CONTEXT_KEY, currentSession.getUser());
                }
            }

            evaluateSingleSignOut(routingContext, endpointHandler -> {
                final String oidcEndSessionEndpoint = endpointHandler.result();
                if (oidcEndSessionEndpoint != null) {
                    // redirect to the OIDC provider to logout the user
                    // this action will return to the AM logout callback to finally logout the user from AM
                    if ("GET".equals(routingContext.request().method().name())) {
                        doRedirect(currentSession != null ? currentSession.getClient() : null, routingContext, oidcEndSessionEndpoint);
                    } else {
                        // if the RP calls the OP with POST method we can't follow redirect
                        // we need to connect to the delegated OP via HTTP call
                        backChannelLogout(routingContext, oidcEndSessionEndpoint);
                    }
                } else {
                    // External OP do not provide EndSessionEndpoint, call the "standard" AM logout mechanism
                    // to invalidate session
                    invalidateSession(routingContext);
                }
            });
        });
    }

    /**
     * Restore current session (user and application) to properly sign out the user.
     *
     * @param routingContext the routing context
     * @param handler handler holding the potential current session
     */
    private void restoreCurrentSession(RoutingContext routingContext, Handler<AsyncResult<UserToken>> handler) {
        // The OP SHOULD accept ID Tokens when the RP identified by the ID Token's aud claim and/or sid claim has a current session
        // or had a recent session at the OP, even when the exp time has passed.
        final String idToken = routingContext.request().getParam(Parameters.ID_TOKEN_HINT);
        if (!StringUtils.isEmpty(idToken)) {
            userService.extractSessionFromIdToken(idToken)
                    .map(userToken -> {
                        // check if the user ids match
                        if (userToken.getUser() != null && routingContext.user() != null) {
                            User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
                            if (!userToken.getUser().getId().equals(endUser.getId())) {
                                throw new UserNotFoundException(userToken.getUser().getId());
                            }
                        }
                        return userToken;
                    })
                    .subscribe(
                            currentSession -> handler.handle(Future.succeededFuture(currentSession)),
                            error -> handler.handle(Future.succeededFuture(new UserToken())));
            return;
        }

        // if no user, continue
        if (routingContext.user() == null) {
            handler.handle(Future.succeededFuture(new UserToken()));
            return;
        }

        // get client from the user's last application
        final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        // whatever is the client search result, we have to return a UserToken with
        // at least the user to manage properly the user's logout information
        clientSyncService.findById(endUser.getClient())
                .switchIfEmpty(Maybe.defer(() -> clientSyncService.findByClientId(endUser.getClient())))
                .subscribe(
                        client -> handler.handle(Future.succeededFuture(new UserToken(endUser, client, null))),
                        error -> handler.handle(Future.succeededFuture(new UserToken(endUser, null, null))),
                        () -> handler.handle(Future.succeededFuture(new UserToken(endUser, null, null))));
    }

    /**
     * Check if the single sign out feature is requested, if yes return the delegated OP end session endpoint URL
     * @param routingContext the routing context
     * @param handler handler holding the potential delegated OP end session endpoint URL
     */
    private void evaluateSingleSignOut(RoutingContext routingContext, Handler<AsyncResult<String>> handler) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final User endUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY) != null ?
                routingContext.get(ConstantKeys.USER_CONTEXT_KEY) :
                (routingContext.user() != null ? ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null);

        // if no client, continue
        if (client == null) {
            handler.handle(Future.succeededFuture());
            return;
        }
        // if single sign out feature disabled, continue
        if (!client.isSingleSignOut()) {
            handler.handle(Future.succeededFuture());
            return;
        }
        // if no user, continue
        if (endUser == null) {
            handler.handle(Future.succeededFuture());
            return;
        }

        // generate the delegated OP logout request
        final Authentication authentication = new EndUserAuthentication(endUser, null, new SimpleAuthenticationContext(new VertxHttpServerRequest(routingContext.request().getDelegate())));
        identityProviderManager.get(endUser.getSource())
                .filter(provider -> provider instanceof SocialAuthenticationProvider)
                .flatMap(provider -> ((SocialAuthenticationProvider) provider).signOutUrl(authentication))
                .flatMap(logoutRequest -> generateLogoutCallback(routingContext, endUser, logoutRequest))
                .subscribe(
                        endpoint -> handler.handle(Future.succeededFuture(endpoint)),
                        err -> {
                            LOGGER.warn("Unable to sign the end user out of the external OIDC '{}', only sign out of AM", client.getClientId(), err);
                            handler.handle(Future.succeededFuture());
                        },
                        () -> handler.handle(Future.succeededFuture())
                );
    }

    private void backChannelLogout(RoutingContext routingContext, String endpoint) {
        final String endpointUri = RequestUtils.getUrlWithoutParameters(endpoint);
        final MultiMap body = RequestUtils.getCleanedQueryParams(endpoint);
        webClient
            .postAbs(endpointUri)
            .rxSendForm(body)
            .subscribe(
                    response -> {
                        if (response.statusCode() >= 400) {
                            logger.warn("Received response from {} endpoint with status code {} and response body {}", endpoint, response.statusCode(), response.bodyAsString());
                        }
                        invalidateSession(routingContext);
                    },
                    error -> {
                        logger.error("An error has occurred when calling the delegated OP end_session_endpoint : {}", endpoint, error);
                        invalidateSession(routingContext);
                    });
    }

    private Maybe<String> generateLogoutCallback(RoutingContext routingContext, User endUser, Request endpoint) {
        // Single Logout can be done only if the endUser profile contains an IdToken.
        if (endUser.getAdditionalInformation() == null) {
            return Maybe.empty();
        }
        if (!endUser.getAdditionalInformation().containsKey(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY)) {
            return Maybe.empty();
        }
        // Generate a state containing provider id and current query parameter string.
        // This state will be sent back to AM after social logout.
        final String delegatedOpIdToken = (String) endUser.getAdditionalInformation().get(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY);
        final JWT stateJwt = new JWT();
        stateJwt.put("c", endUser.getClient());
        stateJwt.put("p", endUser.getSource());
        stateJwt.put("q", routingContext.request().query());
        // remove state from the request to avoid duplicate state parameter into the external idp logout request
        // this state will be restored after the redirect triggered by the external idp
        routingContext.request().params().remove(io.gravitee.am.common.oauth2.Parameters.STATE);
        return jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider())
                .map(state -> {
                    String redirectUri = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/logout/callback");
                    UriBuilder builder = UriBuilder.fromHttpUrl(endpoint.getUri());
                    builder.addParameter(Parameters.POST_LOGOUT_REDIRECT_URI, redirectUri);
                    builder.addParameter(Parameters.ID_TOKEN_HINT, delegatedOpIdToken);
                    builder.addParameter(io.gravitee.am.common.oauth2.Parameters.STATE, state);
                    return builder.buildString();
                })
                .toMaybe();
    }
}
