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

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.context.provider.ClientProperties;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogoutEndpoint.class);
    private static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String INVALIDATE_TOKENS_PARAMETER = "invalidate_tokens";
    private static final String DEFAULT_TARGET_URL = "/";
    private Domain domain;
    private TokenService tokenService;
    private AuditService auditService;
    private ClientSyncService clientSyncService;
    private JWTService jwtService;
    private AuthenticationFlowContextService authenticationFlowContextService;

    public LogoutEndpoint(Domain domain,
                          TokenService tokenService,
                          AuditService auditService,
                          ClientSyncService clientSyncService,
                          JWTService jwtService,
                          AuthenticationFlowContextService authenticationFlowContextService) {
        this.domain = domain;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // fetch client
        fetchClient(routingContext, clientHandler -> {
            final Client client = clientHandler.result();

            // put client in context for later use
            if (client != null) {
                Client safeClient = new Client(client);
                safeClient.setClientSecret(null);
                routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, safeClient);
            }

            // invalidate session
            invalidateSession(routingContext, invalidateSessionHandler -> {
                // invalidate tokens if option is enabled
                if (Boolean.TRUE.valueOf(routingContext.request().getParam(INVALIDATE_TOKENS_PARAMETER))) {
                    invalidateTokens(invalidateSessionHandler.result(), invalidateTokensHandler -> {
                        if (invalidateTokensHandler.failed()) {
                            LOGGER.error("An error occurs while invalidating user tokens", invalidateSessionHandler.cause());
                        }
                        doRedirect(client, routingContext);
                    });
                } else {
                    doRedirect(client, routingContext);
                }
            });
        });
    }

    /**
     * Get client for the current logout request.
     *
     * Client will be used to check the validity of the target_url to avoid potential open redirection.
     *
     * @param routingContext the routing context
     * @param handler handler holding the potential client
     */
    private void fetchClient(RoutingContext routingContext, Handler<AsyncResult<Client>> handler) {
        // The OP SHOULD accept ID Tokens when the RP identified by the ID Token's aud claim and/or sid claim has a current session
        // or had a recent session at the OP, even when the exp time has passed.
        if (routingContext.request().getParam(Parameters.ID_TOKEN_HINT) != null) {
            final String idToken = routingContext.request().getParam(Parameters.ID_TOKEN_HINT);
            jwtService.decode(idToken)
                    .flatMapMaybe(jwt -> clientSyncService.findByClientId(jwt.getAud()))
                    .flatMap(client -> jwtService.decodeAndVerify(idToken, client).toMaybe().map(__ -> client)
                            .onErrorResumeNext(ex -> (ex instanceof ExpiredJWTException) ? Maybe.just(client) : Maybe.error(ex)))
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.succeededFuture()),
                            () -> handler.handle(Future.succeededFuture()));
        } else {
            // if no user, continue
            if (routingContext.user() == null) {
                handler.handle(Future.succeededFuture());
                return;
            }
            // get client from the user's last application
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            clientSyncService.findById(endUser.getClient())
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.succeededFuture()),
                            () -> handler.handle(Future.succeededFuture()));
        }
    }

    /**
     * Redirection to RP After Logout
     *
     * In some cases, the RP will request that the End-User's User Agent to be redirected back to the RP after a logout has been performed.
     *
     * Post-logout redirection is only done when the logout is RP-initiated, in which case the redirection target is the post_logout_redirect_uri parameter value sent by the initiating RP.
     *
     * An id_token_hint carring an ID Token for the RP is also REQUIRED when requesting post-logout redirection;
     * if it is not supplied with post_logout_redirect_uri, the OP MUST NOT perform post-logout redirection.
     *
     * The OP also MUST NOT perform post-logout redirection if the post_logout_redirect_uri value supplied does not exactly match one of the previously registered post_logout_redirect_uris values.
     *
     * The post-logout redirection is performed after the OP has finished notifying the RPs that logged in with the OP for that End-User that they are to log out the End-User.
     *
     * @param client the OAuth 2.0 client
     * @param routingContext the routing context
     */
    private void doRedirect(Client client, RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();

        // validate request
        // see https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout
        // An id_token_hint is REQUIRED when the post_logout_redirect_uri parameter is included.
        // we do not check against the target_url for back-compatibility purpose
        if (request.getParam(Parameters.POST_LOGOUT_REDIRECT_URI) != null &&
                request.getParam(Parameters.ID_TOKEN_HINT) == null) {
            routingContext.fail(new InvalidRequestException("Missing parameter: id_token_hint"));
            return;
        }

        // redirect to target url
        String logoutRedirectUrl = !StringUtils.isEmpty(request.getParam(LOGOUT_URL_PARAMETER)) ?
                request.getParam(LOGOUT_URL_PARAMETER) : (!StringUtils.isEmpty(request.getParam(Parameters.POST_LOGOUT_REDIRECT_URI)) ?
                request.getParam(Parameters.POST_LOGOUT_REDIRECT_URI) : DEFAULT_TARGET_URL);

        // what should we do in this case ?
        // for back-compatibility purpose, only check the target_url parameter
        if (client == null) {
            final String targetUrl = !StringUtils.isEmpty(request.getParam(LOGOUT_URL_PARAMETER)) ? request.getParam(LOGOUT_URL_PARAMETER) : DEFAULT_TARGET_URL;
            doRedirect0(routingContext, targetUrl);
            return;
        }

        // The OP also MUST NOT perform post-logout redirection if the post_logout_redirect_uri value supplied
        // does not exactly match one of the previously registered post_logout_redirect_uris values.
        if (client.getPostLogoutRedirectUris() != null
                && !client.getPostLogoutRedirectUris().isEmpty()
                && !DEFAULT_TARGET_URL.equals(logoutRedirectUrl)) {
            if (client.getPostLogoutRedirectUris()
                    .stream()
                    .noneMatch(registeredClientUri -> logoutRedirectUrl.equals(registeredClientUri))) {
                routingContext.fail(new InvalidRequestException("The post_logout_redirect_uri MUST match the registered callback URL for this application"));
                return;
            }
        }

        // redirect the End-User
        doRedirect0(routingContext, logoutRedirectUrl);
    }

    /**
     * Invalidate session for the current user.
     *
     * @param routingContext the routing context
     * @param handler handler holding the potential End-User
     */
    private void invalidateSession(RoutingContext routingContext, Handler<AsyncResult<User>> handler) {
        io.gravitee.am.model.User endUser = null;
        // clear context and session
        if (routingContext.user() != null) {
            endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // audit event
            report(endUser, routingContext.request());
            // clear user
            routingContext.clearUser();
        }

        if (routingContext.session() != null) {
            // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
            authenticationFlowContextService.clearContext(routingContext.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                    .doOnError((error) -> LOGGER.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                    .subscribe();

            routingContext.session().destroy();
        }

        handler.handle(Future.succeededFuture(endUser));
    }

    /**
     * Invalidate tokens for the current user.
     *
     * @param user the End-User
     * @param handler handler holding the result
     */
    private void invalidateTokens(User user, Handler<AsyncResult<Void>> handler) {
        // if no user, continue
        if (user == null) {
            handler.handle(Future.succeededFuture());
            return;
        }
        tokenService.deleteByUserId(user.getId())
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    /**
     * Report the logout action.
     *
     * @param endUser the End-User
     * @param request the HTTP request
     */
    private void report(User endUser, HttpServerRequest request) {
        auditService.report(
                AuditBuilder.builder(LogoutAuditBuilder.class)
                        .domain(domain.getId())
                        .user(endUser)
                        .ipAddress(RequestUtils.remoteAddress(request))
                        .userAgent(RequestUtils.userAgent(request)));
    }

    private void doRedirect0(RoutingContext routingContext, String url) {
        // state OPTIONAL. Opaque value used by the RP to maintain state between the logout request and the callback to the endpoint specified by the post_logout_redirect_uri parameter.
        // If included in the logout request, the OP passes this value back to the RP using the state parameter when redirecting the User Agent back to the RP.
        UriBuilder uriBuilder = UriBuilder.fromURIString(url);
        final String state = routingContext.request().getParam(io.gravitee.am.common.oauth2.Parameters.STATE);
        if (!StringUtils.isEmpty(state)) {
            uriBuilder.addParameter(io.gravitee.am.common.oauth2.Parameters.STATE, state);
        }

        try {
            routingContext
                    .response()
                    .putHeader(HttpHeaders.LOCATION, uriBuilder.build().toString())
                    .setStatusCode(302)
                    .end();
        } catch (Exception ex) {
            LOGGER.error("An error has occurred during post-logout redirection", ex);
            routingContext.fail(500);
        }
    }
}
