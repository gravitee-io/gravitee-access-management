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

import io.gravitee.am.common.exception.oauth2.BadClientCredentialsException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutCallbackEndpoint extends AbstractLogoutEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(LogoutCallbackEndpoint.class);
    private CertificateManager certificateManager;
    private ClientSyncService clientSyncService;
    private JWTService jwtService;

    public LogoutCallbackEndpoint(Domain domain,
                                  ClientSyncService clientSyncService,
                                  JWTService jwtService,
                                  UserService userService,
                                  AuthenticationFlowContextService authenticationFlowContextService,
                                  CertificateManager certificateManager) {
        super(domain, userService, authenticationFlowContextService);
        this.jwtService = jwtService;
        this.clientSyncService = clientSyncService;
        this.certificateManager = certificateManager;
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
        // First, restore the initial query parameters (those provided when accessing /oauth/authorize on AM side).
        restoreState(routingContext, next -> {
            if (next.failed()) {
                routingContext.fail(next.cause());
                return;
            }
            // restore the session (required for the next steps)
            restoreCurrentSession(routingContext, sessionHandler -> {
                if (sessionHandler.failed()) {
                    routingContext.fail(sessionHandler.cause());
                    return;
                }
                final UserToken currentSession = sessionHandler.result();
                // put current session in context for later use
                if (currentSession.getClient() != null) {
                    Client safeClient = new Client(currentSession.getClient());
                    safeClient.setClientSecret(null);
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, safeClient);
                }
                if (currentSession.getUser() != null) {
                    routingContext.put(ConstantKeys.USER_CONTEXT_KEY, currentSession.getUser());
                }
                // invalidate session
                invalidateSession(routingContext);
            });
        });
    }

    private void restoreState(RoutingContext context, Handler<AsyncResult<Boolean>> handler) {
        final String state = context.request().getParam(Parameters.STATE);

        if (StringUtils.isEmpty(state)) {
            logger.error("No state on login callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Missing state query param")));
            return;
        }

        jwtService.decodeAndVerify(state, certificateManager.defaultCertificateProvider())
                .doOnSuccess(stateJwt -> {
                    final MultiMap initialQueryParams = RequestUtils.getQueryParams((String) stateJwt.getOrDefault("q", ""), false);
                    context.put(ConstantKeys.PARAM_CONTEXT_KEY, initialQueryParams);
                    context.put(ConstantKeys.PROVIDER_ID_PARAM_KEY, stateJwt.get("p"));
                    context.put(Parameters.CLIENT_ID, stateJwt.get("c"));
                })
                .subscribe(
                        stateJwt -> handler.handle(Future.succeededFuture(true)),
                        ex -> {
                            logger.error("An error occurs verifying state on login callback", ex);
                            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
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
        final MultiMap originalLogoutQueryParams = routingContext.get(ConstantKeys.PARAM_CONTEXT_KEY);
        if (originalLogoutQueryParams != null &&
                originalLogoutQueryParams.contains(ConstantKeys.ID_TOKEN_HINT_KEY)) {
            final String idToken = originalLogoutQueryParams.get(ConstantKeys.ID_TOKEN_HINT_KEY);
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

        if (routingContext.get(Parameters.CLIENT_ID) == null) {
            logger.error("Unable to restore client for logout callback");
            handler.handle(Future.failedFuture(new InvalidRequestException("Invalid state")));
            return;
        }

        final User endUser = routingContext.user() != null ? ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null;
        final String clientId = routingContext.get(Parameters.CLIENT_ID);
        clientSyncService.findByClientId(clientId)
                .subscribe(
                        client -> handler.handle(Future.succeededFuture(new UserToken(endUser, client))),
                        ex -> {
                            logger.error("An error has occurred when getting client {}", clientId, ex);
                            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                        },
                        () -> {
                            logger.error("Unknown client {}", clientId);
                            handler.handle(Future.failedFuture(new BadClientCredentialsException()));
                        }
                );
    }
}
