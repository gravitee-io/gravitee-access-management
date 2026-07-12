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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl;

import io.gravitee.am.common.exception.oauth2.InsufficientScopeException;
import io.gravitee.am.common.exception.oauth2.InvalidDPoPProofException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.dpop.DPoPProofValidator;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.core.http.HttpHeaders;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.ACCESS_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.BEARER_AUTH_SCHEME;
import static io.gravitee.am.common.utils.ConstantKeys.DPOP_AUTH_SCHEME;
import static io.gravitee.gateway.api.http.HttpHeaderNames.WWW_AUTHENTICATE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class OAuth2AuthHandlerImpl implements OAuth2AuthHandler {

    private static final String REALM = "gravitee-io";
    private static final String CONTEXT_DPOP_ENFORCED = "dpopEnforced";
    private static final String DPOP_SUPPORTED_ALGS = SignatureAlgorithm.DPOP_SUPPORTED_ALGORITHMS.stream()
            .sorted()
            .collect(Collectors.joining(" "));
    private final OAuth2AuthProvider oAuth2AuthProvider;
    private String requiredScope;
    private boolean extractRawToken;
    private boolean extractToken;
    private boolean extractClient;
    private boolean forceEndUserToken;
    private boolean forceClientToken;
    private boolean selfResource;
    private boolean isSelfResourceAUser;
    private boolean offlineVerification;
    private String resourceParameter;
    private String resourceRequiredScope;
    private DPoPProofValidator dpopProofValidator;
    private final SubjectManager subjectManager;

    private record PresentedToken(String value, String scheme){

        boolean isDPoP(){
            return DPOP_AUTH_SCHEME.equalsIgnoreCase(scheme);
        }
    };

    public OAuth2AuthHandlerImpl(OAuth2AuthProvider oAuth2AuthProvider, SubjectManager subjectManager) {
        this.oAuth2AuthProvider = oAuth2AuthProvider;
        this.subjectManager = subjectManager;
    }

    public OAuth2AuthHandlerImpl(OAuth2AuthProvider oAuth2AuthProvider, String requiredScope, SubjectManager subjectManager) {
        this(oAuth2AuthProvider, subjectManager);
        this.requiredScope = requiredScope;
    }

    @Override
    public void handle(RoutingContext context) {
        parseAuthorization(context, parseHandler -> {
            if (parseHandler.failed()) {
                processException(context, parseHandler.cause());
                return;
            }

            final PresentedToken presented = parseHandler.result();

            // set raw token to the current context
            if (extractRawToken) {
                context.put(ConstantKeys.RAW_TOKEN_CONTEXT_KEY, presented.value());
            }

            oAuth2AuthProvider.decodeToken(presented.value(), offlineVerification, handler -> {
                if (handler.failed()) {
                    processException(context, handler.cause());
                    return;
                }

                OAuth2AuthResponse response = handler.result();
                JWT token = response.getToken();
                Client client = response.getClient();

                // set token to the current context
                if (extractToken) {
                    context.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
                }

                // set client to the current context
                if (extractClient) {
                    context.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                }

                if (token.getDPoPConfirmationThumbprint() != null) {
                    context.put(CONTEXT_DPOP_ENFORCED, true);
                    if (dpopProofValidator == null || !presented.isDPoP()) {
                        processException(context, new InvalidTokenException("A DPoP-bound access token must be presented using the DPoP scheme with a valid proof"));
                        return;
                    }
                    authorizeDPoP(context, presented, token);
                } else {
                    authorize(context, token);
                }
            });
        });
    }

    private void authorizeDPoP(RoutingContext context, PresentedToken presented, JWT token) {
        String thumbprint = token.getDPoPConfirmationThumbprint();
        dpopProofValidator.validateForResource(new VertxHttpServerRequest(context.request().getDelegate()), presented.value(), thumbprint)
                .subscribe(
                        ignored -> authorize(context, token),
                        error -> {
                            if (error instanceof InvalidDPoPProofException) {
                                log.debug("DPoP proof validation failed for a bound access token: {}", error.getMessage());
                                processException(context, new InvalidTokenException(error.getMessage()));
                            } else {
                                log.warn("Unable to validate DPoP-bound access token", error);
                                context.fail(error);
                            }
                        });
    }

    private void authorize(RoutingContext context, JWT token) {
        var single = Single.just(UserId.internal(token.getSub()));
        if (selfResource && isSelfResourceAUser) {
            single = this.subjectManager.findUserIdBySub(token)
                    .switchIfEmpty(single);
        }

        single.subscribe(userId -> {
            // check if current subject can access its own resources
            if (selfResource) {
                final String resourceId = context.request().getParam(resourceParameter);
                // since Domain V2, sub claim is not the userId anymore
                // we have to check if userId provided as resourceId match the userId internal ID found using the sub claim
                // and we also have to if the resourceId match the
                boolean isUserValid = isSelfResourceAUser && resourceId != null && resourceId.equals(userId.id());
                boolean isResourceIdValid = resourceId != null && (resourceId.equals(token.getSub()) || resourceId.equals(token.getInternalSub()));
                boolean hasRequiredScope = resourceRequiredScope == null || token.hasScope(resourceRequiredScope);

                if ((isUserValid || isResourceIdValid) && hasRequiredScope) {
                    context.next();
                    return;
                }
            }

            if (forceEndUserToken && token.getSub().equals(token.getAud())) {
                // token for end userId must not contain clientId as subject
                processException(context, new InvalidTokenException("The access token was not issued for an End-User"));
                return;
            }

            if (forceClientToken && !token.getSub().equals(token.getAud())) {
                // token for end userId must not contain clientId as subject
                processException(context, new InvalidTokenException("The access token was not issued for a Client"));
                return;
            }

            // check required scope
            if (requiredScope != null && !token.hasScope(requiredScope)) {
                processException(context, new InsufficientScopeException("Invalid access token scopes. The access token should have at least '" + requiredScope + "' scope"));
                return;
            }
            context.next();
        }, error -> {
            log.warn("Unable to validate OAuth2 authorization", error);
            context.fail(error);
        });
    }

    public void extractRawToken(boolean extractRawToken) {
        this.extractRawToken = extractRawToken;
    }

    public void extractToken(boolean extractToken) {
        this.extractToken = extractToken;
    }

    public void extractClient(boolean extractClient) {
        this.extractClient = extractClient;
    }

    public void forceEndUserToken(boolean forceEndUserToken) {
        this.forceEndUserToken = forceEndUserToken;
    }

    public void forceClientToken(boolean forceClientToken) {
        this.forceClientToken = forceClientToken;
    }

    public void selfResource(boolean selfResource, String resourceParameter, boolean asUser) {
        this.selfResource = selfResource;
        this.isSelfResourceAUser = asUser;
        this.resourceParameter = resourceParameter;
    }

    public void selfResource(boolean selfResource, String resourceParameter, String requiredScope, boolean asUser) {
        selfResource(selfResource, resourceParameter, asUser);
        this.resourceRequiredScope = requiredScope;
    }

    public void offlineVerification(boolean offlineVerification) {
        this.offlineVerification = offlineVerification;
    }

    public void dpopProofValidator(DPoPProofValidator dpopProofValidator) {
        this.dpopProofValidator = dpopProofValidator;
    }

    private void parseAuthorization(RoutingContext context, Handler<AsyncResult<PresentedToken>> handler) {
        final HttpServerRequest request = context.request();
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
        String authToken;
        String authScheme;
        try {
            if (authorization != null) {
                // authorization header has been found check the value
                int idx = authorization.indexOf(' ');

                if (idx <= 0) {
                    handler.handle(Future.failedFuture(new InvalidRequestException("The access token must be sent using the Authorization header field")));
                    return;
                }

                authScheme = authorization.substring(0, idx);
                if (!BEARER_AUTH_SCHEME.equalsIgnoreCase(authScheme) && !DPOP_AUTH_SCHEME.equalsIgnoreCase(authScheme)) {
                    handler.handle(Future.failedFuture(new InvalidTokenException("Authorization header must use the Bearer or DPoP scheme")));
                    return;
                }
                authToken = authorization.substring(idx + 1);
            } else {
                // if no authorization header found, check authorization in body
                authToken = request.getParam(ACCESS_TOKEN_KEY);
                authScheme = null;
            }

            if (authToken == null) {
                handler.handle(Future.failedFuture(new InvalidTokenException("Missing access token. The access token must be sent using the Authorization header field (Bearer scheme) or the 'access_token' body parameter")));
                return;
            }

            handler.handle(Future.succeededFuture(new PresentedToken(authToken, authScheme)));
        } catch (RuntimeException e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void processException(RoutingContext context, Throwable exception) {
        int statusCode = -1;
        if (exception instanceof HttpException) {
            statusCode = ((HttpException) exception).getStatusCode();
        } else if (exception instanceof OAuth2Exception) {
            statusCode = ((OAuth2Exception) exception).getHttpStatusCode();
        }

        if (statusCode == 401) {
            context.response().putHeader(WWW_AUTHENTICATE, authenticateHeader(context, exception));
        }

        context.fail(exception);
    }

    private String authenticateHeader(RoutingContext context, Throwable exception) {
        if (Boolean.TRUE.equals(context.get(CONTEXT_DPOP_ENFORCED))) {
            final String error = exception instanceof OAuth2Exception oauth2Exception
                    ? oauth2Exception.getOAuth2ErrorCode()
                    : ConstantKeys.INVALID_TOKEN;
            return "DPoP error=\"" + error + "\", algs=\"" + DPOP_SUPPORTED_ALGS + "\"";
        }
        return "Bearer realm=\"" + REALM + "\"";
    }

}
