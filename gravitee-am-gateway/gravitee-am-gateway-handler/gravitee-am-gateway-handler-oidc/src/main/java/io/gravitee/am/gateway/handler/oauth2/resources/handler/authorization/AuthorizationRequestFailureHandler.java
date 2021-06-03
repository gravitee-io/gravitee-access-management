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

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.JWTOAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.resources.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isHybridFlow;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isImplicitFlow;

/**
 * If the request fails due to a missing, invalid, or mismatching redirection URI, or if the client identifier is missing or invalid,
 * the authorization server SHOULD inform the resource owner of the error and MUST NOT automatically redirect the user-agent to the
 * invalid redirection URI.
 *
 * If the resource owner denies the access request or if the request fails for reasons other than a missing or invalid redirection URI,
 * the authorization server informs the client by adding the following parameters to the fragment component of the redirection URI using the
 * "application/x-www-form-urlencoded" format
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2.2.1">4.2.2.1. Error Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestFailureHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestFailureHandler.class);
    private static final String ERROR_ENDPOINT = "/oauth/error";
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private final JWTService jwtService;
    private final JWEService jweService;
    private final OpenIDDiscoveryService openIDDiscoveryService;

    public AuthorizationRequestFailureHandler(final OpenIDDiscoveryService openIDDiscoveryService,
                                              final JWTService jwtService,
                                              final JWEService jweService) {
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.jwtService = jwtService;
        this.jweService = jweService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {

            try {
                AuthorizationRequest request = resolveInitialAuthorizeRequest(routingContext);
                Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
                String defaultErrorURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + ERROR_ENDPOINT);
                Throwable throwable = routingContext.failure();
                if (throwable instanceof OAuth2Exception) {
                    OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                    // Manage exception
                    processOAuth2Exception(request, oAuth2Exception, client, defaultErrorURL, routingContext, h -> {
                        if (h.failed()) {
                            logger.error("An error has occurred while handling authorization error response", h.cause());
                            routingContext.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500).end();
                            return;
                        }
                        // redirect user to the error page with error code and description
                        doRedirect(routingContext, h.result());
                    });
                } else if (throwable instanceof HttpException) {
                    // in case of http status exception, go to the default error page
                    request.setRedirectUri(defaultErrorURL);
                    HttpException httpStatusException = (HttpException) throwable;
                    doRedirect(routingContext, buildRedirectUri(httpStatusException.getMessage(), httpStatusException.getPayload(), request, routingContext));
                } else {
                    logger.error("An exception has occurred while handling authorization request", throwable);
                    cleanSession(routingContext);
                    if (routingContext.statusCode() != -1) {
                        routingContext
                                .response()
                                .setStatusCode(routingContext.statusCode())
                                .end();
                    } else {
                        routingContext
                                .response()
                                .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                                .end();
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to handle authorization error response", e);
                doRedirect(routingContext, routingContext.get(CONTEXT_PATH) + ERROR_ENDPOINT);
            }
        }
    }

    private void processOAuth2Exception(AuthorizationRequest authorizationRequest,
                                        OAuth2Exception oAuth2Exception,
                                        Client client,
                                        String defaultErrorURL,
                                        RoutingContext context,
                                        Handler<AsyncResult<String>> handler) {
        final String clientId = authorizationRequest.getClientId();

        // no client available or missing redirect_uri, go to default error page
        if (clientId == null || client == null || authorizationRequest.getRedirectUri() == null) {
            authorizationRequest.setRedirectUri(defaultErrorURL);
        }
        // user set a wrong redirect_uri, go to default error page
        if (oAuth2Exception instanceof RedirectMismatchException) {
            authorizationRequest.setRedirectUri(defaultErrorURL);
        }

        // Process error response
        try {
            // Response Mode is not supplied by the client, process the response as usual
            if (client == null || authorizationRequest.getResponseMode() == null || !authorizationRequest.getResponseMode().endsWith("jwt")) {
                // redirect user
                handler.handle(Future.succeededFuture(buildRedirectUri(oAuth2Exception.getOAuth2ErrorCode(), oAuth2Exception.getMessage(), authorizationRequest, context)));
                return;
            }

            // Otherwise the JWT contains the error response parameters
            JWTOAuth2Exception jwtException = new JWTOAuth2Exception(oAuth2Exception, authorizationRequest.getState());
            jwtException.setIss(openIDDiscoveryService.getIssuer(authorizationRequest.getOrigin()));
            jwtException.setAud(client.getClientId());

            // There is nothing about expiration. We admit to use the one settled for IdToken validity
            jwtException.setExp(Instant.now().plusSeconds(client.getIdTokenValiditySeconds()).getEpochSecond());

            // Sign if needed, else return unsigned JWT
            jwtService.encodeAuthorization(jwtException.build(), client)
                    // Encrypt if needed, else return JWT
                    .flatMap(authorization -> jweService.encryptAuthorization(authorization, client))
                    .subscribe(
                            jwt -> handler.handle(Future.succeededFuture(
                                    jwtException.buildRedirectUri(
                                            authorizationRequest.getRedirectUri(),
                                            authorizationRequest.getResponseType(),
                                            authorizationRequest.getResponseMode(),
                                            jwt))),
                            ex -> handler.handle(Future.failedFuture(ex)));
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private String buildRedirectUri(String error, String errorDescription, AuthorizationRequest authorizationRequest, RoutingContext context) throws URISyntaxException {
        String errorPath = context.get(CONTEXT_PATH) + ERROR_ENDPOINT;

        // prepare query
        Map<String, String> query = new LinkedHashMap<>();
        // put client_id parameter for the default error page for branding/custom html purpose
        if (isDefaultErrorPage(authorizationRequest.getRedirectUri(), errorPath)) {
            query.computeIfAbsent(Parameters.CLIENT_ID, val -> authorizationRequest.getClientId());
        }

        query.computeIfAbsent("error", val -> error);
        query.computeIfAbsent("error_description", val -> errorDescription);
        query.computeIfAbsent(Parameters.STATE, val -> authorizationRequest.getState());

        boolean fragment = !isDefaultErrorPage(authorizationRequest.getRedirectUri(), errorPath) &&
                (isImplicitFlow(authorizationRequest.getResponseType()) || isHybridFlow(authorizationRequest.getResponseType()));
        return append(authorizationRequest.getRedirectUri(), query, fragment);
    }

    private String append(String base, Map<String, String> query, boolean fragment) throws URISyntaxException {
        // prepare final redirect uri
        UriBuilder template = UriBuilder.newInstance();

        // get URI from the redirect_uri parameter
        UriBuilder builder = UriBuilder.fromURIString(base);
        URI redirectUri = builder.build();

        // create final redirect uri
        template.scheme(redirectUri.getScheme())
                .host(redirectUri.getHost())
                .port(redirectUri.getPort())
                .userInfo(redirectUri.getUserInfo())
                .path(redirectUri.getPath());

        // append error parameters in "application/x-www-form-urlencoded" format
        if (fragment) {
            query.forEach((k, v) -> template.addFragmentParameter(k, UriBuilder.encodeURIComponent(v)));
        } else {
            query.forEach((k, v) -> template.addParameter(k, UriBuilder.encodeURIComponent(v)));
        }
        return template.build().toString();
    }

    private AuthorizationRequest resolveInitialAuthorizeRequest(RoutingContext routingContext) {
        AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        // we have the authorization request in session if we come from the approval user page
        if (authorizationRequest != null) {
            return authorizationRequest;
        }

        // if none, we have the required request parameters to re-create the authorize request
        return authorizationRequestFactory.create(routingContext);
    }

    private boolean isDefaultErrorPage(String redirectUri, String errorPath) {
        return redirectUri.contains(errorPath);
    }

    private void cleanSession(RoutingContext context) {
        context.session().remove(ConstantKeys.TRANSACTION_ID_KEY);
        context.session().remove(ConstantKeys.USER_CONSENT_COMPLETED_KEY);
        context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID);
        context.session().remove(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY);
    }

    private void doRedirect(RoutingContext context, String url) {
        cleanSession(context);
        context.response().putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
