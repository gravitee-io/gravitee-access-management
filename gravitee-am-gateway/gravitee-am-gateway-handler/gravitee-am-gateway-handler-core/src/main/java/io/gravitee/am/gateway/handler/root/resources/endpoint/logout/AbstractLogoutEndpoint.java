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

import com.google.common.base.Strings;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractLogoutEndpoint implements Handler<RoutingContext> {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private static final String INVALIDATE_TOKENS_PARAMETER = "invalidate_tokens";
    protected static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String DEFAULT_TARGET_URL = "/";

    protected Domain domain;
    private final AuthenticationFlowContextService authenticationFlowContextService;
    protected UserService userService;

    protected AbstractLogoutEndpoint(Domain domain,
                                  UserService userService,
                                  AuthenticationFlowContextService authenticationFlowContextService) {
        this.domain = domain;
        this.userService = userService;
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
            for (Map.Entry<String, String> entry : originalLogoutQueryParams.entries()) {
                if (!(LOGOUT_URL_PARAMETER.equals(entry.getKey()) || Parameters.POST_LOGOUT_REDIRECT_URI.equals(entry.getKey()))) {
                    routingContext.request().params().add(entry.getKey(), originalLogoutQueryParams.get(entry.getKey()));
                }
            }
        }

        // The OP also MUST NOT perform post-logout redirection if the post_logout_redirect_uri value supplied
        // does not exactly match one of the previously registered post_logout_redirect_uris values.
        // if client is null, check security domain options


        List<String> registeredUris = client != null && !isEmpty(client.getPostLogoutRedirectUris()) ? client.getPostLogoutRedirectUris() : (domain.getOidc() != null ? domain.getOidc().getPostLogoutRedirectUris() : null);
        if (!isMatchingRedirectUri(logoutRedirectUrl, registeredUris, domain.isRedirectUriStrictMatching() || domain.usePlainFapiProfile())) {
            routingContext.fail(new InvalidRequestException("The post_logout_redirect_uri MUST match the registered callback URLs"));
            return;
        }

        try {
            // use UriBuilder to sanitize the uri so non urlEncoded character will be encoded
            // to avoid URISyntaxException due to the space in the scope parameter value
            LOGGER.info("Parsing logoutRedirectUrl {}", logoutRedirectUrl);
            URI uri = UriBuilder.fromURIString(logoutRedirectUrl).build();
            if(uri.getUserInfo() != null){
                routingContext.fail(new RedirectMismatchException(String.format("The post_logout_redirect_uri [ %s ] MUST NOT contain userInfo part", logoutRedirectUrl)));
                return;
            }
        } catch (URISyntaxException ex){
            // the URI is invalid, only log the error to avoid regression
            // URI white list will reject the value if necessary
            LOGGER.warn("The post_logout_redirect_uri [{}] has syntax error redirect to error page: {}", logoutRedirectUrl, ex.getMessage());
            routingContext.fail(new RedirectMismatchException(String.format("The post_logout_redirect_uri [ %s ] MUST NOT contain userInfo part", logoutRedirectUrl)));
            return;
        }

        // redirect the End-User
        doRedirect0(routingContext, endSessionEndpoint == null ? logoutRedirectUrl : endSessionEndpoint);
    }

    private String getLogoutRedirectUrl(MultiMap params) {
        String logoutUrl = params.get(LOGOUT_URL_PARAMETER);
        if (StringUtils.hasText(logoutUrl)) {
            return logoutUrl;
        }

        String postLogoutRedirectUri = params.get(Parameters.POST_LOGOUT_REDIRECT_URI);
        if (StringUtils.hasText(postLogoutRedirectUri)) {
            return postLogoutRedirectUri;
        }

        return DEFAULT_TARGET_URL;
    }

    /**
     * Invalidate session for the current user
     *
     * @param routingContext the routing context
     * @param redirect flag to redirect the user after the logout action
     */
    protected void invalidateSession(RoutingContext routingContext, boolean redirect) {
        invalidateSession0(routingContext, redirect);
    }

    /**
     * Invalidate session for the current user
     *
     * @param routingContext the routing context
     */
    protected void invalidateSession(RoutingContext routingContext) {
        invalidateSession0(routingContext, true);
    }

    private void invalidateSession0(RoutingContext routingContext, boolean redirect) {
        final User endUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY) != null ?
                routingContext.get(ConstantKeys.USER_CONTEXT_KEY) :
                (routingContext.user() != null ? ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser() : null);

        // clear context and session
        Completable clearSessionCompletable = endUser != null ? userService.logout(endUser, needToInvalidateTokens(routingContext), getAuthenticatedUser(endUser, routingContext)) : Completable.complete();
        Completable clearContextCompletable = routingContext.session() != null ? authenticationFlowContextService.clearContext(routingContext.session().get(ConstantKeys.TRANSACTION_ID_KEY)) : Completable.complete();

        clearSessionCompletable
                .andThen(clearContextCompletable)
                .onErrorComplete()
                .subscribe(
                        () -> {
                            routingContext.clearUser();
                            if (routingContext.session() != null) {
                                routingContext.session().destroy();
                            }
                            if (redirect) {
                                doRedirect(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY), routingContext);
                            } else {
                                routingContext.response().setStatusCode(200).end();
                            }
                        },
                        routingContext::fail
                );
    }

    private boolean needToInvalidateTokens(RoutingContext routingContext) {
        // look at the invalidate_tokens parameter into the current URL or ino the initial parameters
        // if the Single SignOut is enabled.
        String invalidateTokens = routingContext.request().getParam(INVALIDATE_TOKENS_PARAMETER);
        if (Strings.isNullOrEmpty(invalidateTokens) && routingContext.data().containsKey(ConstantKeys.PARAM_CONTEXT_KEY)) {
            MultiMap initialQueryParam = routingContext.get(ConstantKeys.PARAM_CONTEXT_KEY);
            if (initialQueryParam.contains(INVALIDATE_TOKENS_PARAMETER)) {
                invalidateTokens = initialQueryParam.get(INVALIDATE_TOKENS_PARAMETER);
            }
        }
        return Boolean.parseBoolean(invalidateTokens);
    }

    private void doRedirect0(RoutingContext routingContext, String url) {
        // state OPTIONAL. Opaque value used by the RP to maintain state between the logout request and the callback to the endpoint specified by the post_logout_redirect_uri parameter.
        // If included in the logout request, the OP passes this value back to the RP using the state parameter when redirecting the User Agent back to the RP.
        LOGGER.info("Parsing final redirect url {}", url);
        UriBuilder uriBuilder = UriBuilder.fromURIString(url);
        final String state = routingContext.request().getParam(io.gravitee.am.common.oauth2.Parameters.STATE);
        if (StringUtils.hasText(state)) {
            uriBuilder.addParameter(io.gravitee.am.common.oauth2.Parameters.STATE, state);
        }

        try {
            routingContext
                    .response()
                    .putHeader(HttpHeaders.LOCATION, uriBuilder.buildString())
                    .setStatusCode(302)
                    .end();
        } catch (Exception ex) {
            LOGGER.error("An error has occurred during post-logout redirection", ex);
            routingContext.fail(500);
        }
    }

    private boolean isMatchingRedirectUri(String requestedRedirectUri, List<String> registeredRedirectUris, boolean uriStrictMatch) {
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
                .anyMatch(registeredUri -> ParamUtils.redirectMatches(requestedRedirectUri, registeredUri, uriStrictMatch));

    }

    private io.gravitee.am.identityprovider.api.User getAuthenticatedUser(User endUser, RoutingContext routingContext) {
        // override principal user
        DefaultUser principal = new DefaultUser(endUser.getUsername());
        principal.setId(endUser.getId());
        Map<String, Object> additionalInformation = new HashMap<>();
        if (routingContext.session() != null) {
            if (canSaveIp(routingContext)) {
                additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(routingContext.request()));
            }
            if (canSaveUserAgent(routingContext)) {
                additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(routingContext.request()));
            }
        }
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        if (!ObjectUtils.isEmpty(endUser.getDisplayName())) {
            additionalInformation.put(StandardClaims.NAME, endUser.getDisplayName());
        }
        principal.setAdditionalInformation(additionalInformation);
        return principal;
    }
}
