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

import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.exception.oauth2.ReturnUrlMismatchException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.ResponseMode;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.ErrorInfo;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.JWTOAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.resources.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.OAuth2ErrorResponse;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.oauth2.GrantType.CLIENT_CREDENTIALS;
import static io.gravitee.am.common.oauth2.GrantType.JWT_BEARER;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isHybridFlow;
import static io.gravitee.am.service.utils.ResponseTypeUtils.isImplicitFlow;
import static org.springframework.util.StringUtils.hasLength;

/**
 * If the request fails due to a missing, invalid, or mismatching redirection URI, or if the client identifier is missing or invalid,
 * the authorization server SHOULD inform the resource owner of the error and MUST NOT automatically redirect the user-agent to the
 * invalid redirection URI.
 * <p>
 * If the resource owner denies the access request or if the request fails for reasons other than a missing or invalid redirection URI,
 * the authorization server informs the client by adding the following parameters to the fragment component of the redirection URI using the
 * "application/x-www-form-urlencoded" format
 * <p>
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
    private final Environment environment;
    private final int codeValidityInSec;

    public AuthorizationRequestFailureHandler(final OpenIDDiscoveryService openIDDiscoveryService,
                                              final JWTService jwtService,
                                              final JWEService jweService,
                                              final Environment environment) {
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.jwtService = jwtService;
        this.jweService = jweService;
        this.environment = environment;
        this.codeValidityInSec = this.environment.getProperty("authorization.code.validity", Integer.class, 60000) / 1000;
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
                } else if (throwable instanceof PolicyChainException) {
                    PolicyChainException policyChainException = (PolicyChainException) throwable;
                    OAuth2ErrorResponse oAuth2ErrorResponse = new OAuth2ErrorResponse(policyChainException.key());
                    oAuth2ErrorResponse.setDescription(policyChainException.getMessage());
                    routingContext
                            .response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                            .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                            .putHeader(HttpHeaders.PRAGMA, "no-cache")
                            .setStatusCode(policyChainException.statusCode())
                            .end(Json.encodePrettily(oAuth2ErrorResponse));
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
        if (oAuth2Exception instanceof RedirectMismatchException || oAuth2Exception instanceof ReturnUrlMismatchException) {
            authorizationRequest.setRedirectUri(defaultErrorURL);
        }
        // check if the redirect_uri request parameter is allowed
        if (isForbiddenRedirectUri(client, authorizationRequest) || invalidParamRedirectURI(client, authorizationRequest.getRedirectUri())) {
            authorizationRequest.setRedirectUri(defaultErrorURL);
        }
        // InvalidRequestObjectException without the RequestObject present into the Context means that the JWT can't be decoded
        // return to the default error page to avoid redirect using wrong response mode
        if (oAuth2Exception instanceof InvalidRequestObjectException && context.get(ConstantKeys.REQUEST_OBJECT_KEY) == null) {
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

            // There is nothing about expiration. We admit to use the one settled for authorization code validity
            jwtException.setExp(Instant.now().plusSeconds(this.codeValidityInSec).getEpochSecond());

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

        var errorInfo = new ErrorInfo(error, null, errorDescription, authorizationRequest.getState());


        boolean fragment = !isDefaultErrorPage(authorizationRequest.getRedirectUri(), errorPath) && requiresFragment(authorizationRequest);
        Map<String, String> extraParams = new HashMap<>();
        if (isDefaultErrorPage(authorizationRequest.getRedirectUri(), errorPath)) {
            extraParams.put(Parameters.CLIENT_ID, authorizationRequest.getClientId());
        }
        addErrorToSession(context, errorInfo);
        return UriBuilder.buildErrorRedirect(authorizationRequest.getRedirectUri(), errorInfo, fragment, extraParams);
    }

    private boolean requiresFragment(AuthorizationRequest authorizationRequest) {
        return (!hasLength(authorizationRequest.getResponseMode()) && (isImplicitFlow(authorizationRequest.getResponseType())) || isHybridFlow(authorizationRequest.getResponseType()))
                || ResponseMode.FRAGMENT.equals(authorizationRequest.getResponseMode());
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
        if (context.session() != null) {
            context.session().remove(ConstantKeys.TRANSACTION_ID_KEY);
            context.session().remove(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY);
            context.session().remove(ConstantKeys.USER_CONSENT_COMPLETED_KEY);
            context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY);
            context.session().remove(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
            context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
            context.session().remove(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY);
            context.session().remove(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY);
            context.session().remove(ConstantKeys.USER_LOGIN_COMPLETED_KEY);
            context.session().remove(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY);
        }
    }

    private void doRedirect(RoutingContext context, String url) {
        cleanSession(context);
        context.response().putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private boolean isForbiddenRedirectUri(Client client, AuthorizationRequest authorizationRequest) {
        return client != null
                && client.getRedirectUris() != null
                && authorizationRequest.getRedirectUri() != null
                && !client.getRedirectUris().contains(authorizationRequest.getRedirectUri());
    }

    /**
     * Application, only with 'client_credentials' or 'urn:ietf:params:oauth:grant-type:jwt-bearer' grant type cannot have
     * `redirect_uri` in the request
     */
    private boolean invalidParamRedirectURI(Client client, String redirectURI) {
        return client != null
                && client.getAuthorizedGrantTypes() != null
                && client.getAuthorizedGrantTypes().stream()
                .filter(grantType -> !(CLIENT_CREDENTIALS.equals(grantType) || grantType.startsWith(JWT_BEARER)))
                .findFirst()
                .isEmpty()
                && redirectURI != null;
    }

    private void addErrorToSession(RoutingContext context, ErrorInfo errorInfo) {
        StringBuilder errorBuilder = new StringBuilder();
        if (errorInfo.error() != null) {
            errorBuilder.append(errorInfo.error());
        }
        if (errorInfo.description() != null) {
            errorBuilder.append("$");
            errorBuilder.append(errorInfo.description());
        }
        if (!errorBuilder.isEmpty()) {
            context.session().put(ERROR_HASH, HashUtil.generateSHA256(errorBuilder.toString()));
        }

    }
}
