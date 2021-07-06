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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractLogoutEndpoint implements Handler<RoutingContext> {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private static final String INVALIDATE_TOKENS_PARAMETER = "invalidate_tokens";
    private static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String DEFAULT_TARGET_URL = "/";

    private Domain domain;
    private TokenService tokenService;
    private AuditService auditService;
    private AuthenticationFlowContextService authenticationFlowContextService;

    public AbstractLogoutEndpoint(Domain domain, TokenService tokenService, AuditService auditService, AuthenticationFlowContextService authenticationFlowContextService) {
        this.domain = domain;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    protected void doRedirect(Client client, RoutingContext routingContext) {
        doRedirect(client, routingContext, null);
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
     * @param endSessionEndpoint the End Session Endpoint of the OIDC provider providing the User info
     */
    protected void doRedirect(Client client, RoutingContext routingContext, String endSessionEndpoint) {
        final HttpServerRequest request = routingContext.request();

        // validate request
        // see https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout
        // An id_token_hint is REQUIRED when the post_logout_redirect_uri parameter is included.
        // for back-compatibility purpose, we skip this validation
        // see https://github.com/gravitee-io/issues/issues/5163
        /*if (request.getParam(Parameters.POST_LOGOUT_REDIRECT_URI) != null &&
                request.getParam(Parameters.ID_TOKEN_HINT) == null) {
            routingContext.fail(new InvalidRequestException("Missing parameter: id_token_hint"));
            return;
        }*/

        // redirect to target url
        String logoutRedirectUrl = getLogoutRedirectUrl(request.params());
        final MultiMap originalLogoutQueryParams = routingContext.get(ConstantKeys.PARAM_CONTEXT_KEY);
        if (originalLogoutQueryParams != null) {
            // redirect is trigger because of the LogoutCallbackEndpoint, extract the redirect URL from initial logout request
            logoutRedirectUrl = getLogoutRedirectUrl(originalLogoutQueryParams);
            // clear state set by AM during the OP EndUserSession call
            routingContext.request().params().remove(io.gravitee.am.common.oauth2.Parameters.STATE);
            // restore parameters from the original logout request
            for (Map.Entry<String, String> entry : originalLogoutQueryParams.entries()){
                if (!(LOGOUT_URL_PARAMETER.equals(entry.getKey()) || Parameters.POST_LOGOUT_REDIRECT_URI.equals(entry.getKey()))) {
                    routingContext.request().params().add(entry.getKey(), originalLogoutQueryParams.get(entry.getKey()));
                }
            }
        }

        // The OP also MUST NOT perform post-logout redirection if the post_logout_redirect_uri value supplied
        // does not exactly match one of the previously registered post_logout_redirect_uris values.
        // if client is null, check security domain options
        List<String> registeredUris = client != null ? client.getPostLogoutRedirectUris() :
                (domain.getOidc() != null ? domain.getOidc().getPostLogoutRedirectUris() : null);
        if (!isMatchingRedirectUri(logoutRedirectUrl, registeredUris)) {
            routingContext.fail(new InvalidRequestException("The post_logout_redirect_uri MUST match the registered callback URLs"));
            return;
        }

        // redirect the End-User
        doRedirect0(routingContext, endSessionEndpoint == null ? logoutRedirectUrl : endSessionEndpoint);
    }

    private String getLogoutRedirectUrl(MultiMap params) {
        String logoutRedirectUrl = !StringUtils.isEmpty(params.get(LOGOUT_URL_PARAMETER)) ?
                params.get(LOGOUT_URL_PARAMETER) : (!StringUtils.isEmpty(params.get(Parameters.POST_LOGOUT_REDIRECT_URI)) ?
                params.get(Parameters.POST_LOGOUT_REDIRECT_URI) : DEFAULT_TARGET_URL);
        return logoutRedirectUrl;
    }

    /**
     * Invalidate session for the current user.
     *
     * @param routingContext the routing context
     * @param handler handler holding the potential End-User
     */
    protected void invalidateSession(RoutingContext routingContext, Handler<AsyncResult<User>> handler) {
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
    protected void invalidateTokens(User user, Handler<AsyncResult<Void>> handler) {
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
    protected void report(User endUser, HttpServerRequest request) {
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

    private boolean isMatchingRedirectUri(String requestedRedirectUri, List<String> registeredRedirectUris) {
        // no registered uris to check, continue
        if (registeredRedirectUris == null) {
            return true;
        }
        // no registered uris to check, continue
        if (registeredRedirectUris.isEmpty()) {
            return true;
        }
        // default value, continue
        if (DEFAULT_TARGET_URL.equals(requestedRedirectUri)) {
            return true;
        }
        // compare values
        return registeredRedirectUris
                .stream()
                .anyMatch(registeredUri -> requestedRedirectUri.equals(registeredUri));
    }

    protected Handler<AsyncResult<User>> invalidSessionHandler(RoutingContext routingContext, Client client) {
        return invalidateSessionHandler -> {
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
        };
    }
}
