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

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.text.ParseException;
import java.util.*;

import static io.gravitee.am.common.utils.ConstantKeys.*;

/**
 * The request Authorization Request parameter enables OpenID Connect requests to be passed in a single,
 * self-contained parameter and to be optionally signed and/or encrypted. It represents the request as a JWT whose
 * Claims are the request parameters specified in Section 3.1.2. This JWT is called a Request Object.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#JWTRequests">6.Passing Request Parameters as JWTs</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseRequestObjectHandler extends AbstractAuthorizationRequestHandler implements Handler<RoutingContext> {
    private static final String HTTPS_SCHEME  = "https";

    public static final int ONE_HOUR_IN_MILLIS = 3600 * 1000;

    private final RequestObjectService requestObjectService;
    private final PushedAuthorizationRequestService parService;

    private final Domain domain;

    public AuthorizationRequestParseRequestObjectHandler(RequestObjectService requestObjectService, Domain domain, PushedAuthorizationRequestService parService) {
        this.requestObjectService = requestObjectService;
        this.domain = domain;
        this.parService = parService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String request = context.request().getParam(Parameters.REQUEST);
        final String requestUri = context.request().getParam(Parameters.REQUEST_URI);
        final Client client = context.get(CLIENT_CONTEXT_KEY);
        if (StringUtils.isEmpty(requestUri)) {
            if (client != null && client.isRequireParRequest()) {
                context.fail(new InvalidRequestException("Client requires pushed authorization requests, request_uri is expected"));
                return;
            }
            if (StringUtils.isEmpty(request)) {
                if (this.domain.usePlainFapiProfile()) {
                    // according to https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server
                    // Authorization Server shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter;
                    context.fail(new InvalidRequestException("Missing parameter: request or request_uri is required for FAPI"));
                    return;
                }
                // if there is no request or request_uri parameters, continue
                context.next();
                return;
            }
        }

        // check request object parameters
        checkRequestObjectParameters(request, requestUri);

        // Proceed request and request_uri parameters
        Maybe<JWT> requestObject;

        if (!StringUtils.isEmpty(request)) {
            context.put(REQUEST_OBJECT_FROM_URI, false);
            requestObject = handleRequestObjectValue(context, request);
        } else {
            context.put(REQUEST_OBJECT_FROM_URI, true);
            requestObject = handleRequestObjectURI(context, requestUri);
        }

        requestObject
                .subscribe(
                        jwt -> {
                            try {
                                // Check OAuth2 parameters
                                checkOAuthParameters(context, jwt);
                                context.next();
                            } catch (Exception ex) {
                                context.fail(ex);
                            }
                        },
                        context::fail,
                        context::next);
    }

    private void checkRequestObjectParameters(String request, String requestUri) {
        // Requests using these parameters are represented as JWTs, which are respectively passed by value or by
        // reference. The ability to pass requests by reference is particularly useful for large requests. If one of
        // these parameters is used, the other MUST NOT be used in the same request.
        if (!StringUtils.isEmpty(request) && !StringUtils.isEmpty(requestUri)) {
            throw new InvalidRequestException("request and request_uri parameters must not be use in the same request");
        }

        if (requestUri != null) {
            // The entire Request URI MUST NOT exceed 512 ASCII characters.
            if (requestUri.length() > 512) {
                throw new InvalidRequestUriException("request_uri parameter must not exceed 512 ASCII characters");
            }

            try {
                URI uri = URI.create(requestUri);

                // The scheme used in the request_uri value MUST be https or starts with urn:ros:
                if (uri.getScheme() == null ||
                        (!uri.getScheme().equalsIgnoreCase(HTTPS_SCHEME) &&
                                !requestUri.startsWith(RequestObjectService.RESOURCE_OBJECT_URN_PREFIX) &&
                                !requestUri.startsWith(PushedAuthorizationRequestService.PAR_URN_PREFIX))) {
                    throw new InvalidRequestUriException("request_uri parameter scheme must be HTTPS");
                }
            } catch (IllegalArgumentException iae) {
                throw new InvalidRequestUriException("request_uri parameter is not valid");
            }
        }
    }

    private Maybe<JWT> handleRequestObjectValue(RoutingContext context, String request) {
        if (request != null) {
            // Ensure that the request_uri is not propagated to the next authorization flow step
            context.request().params().remove(Parameters.REQUEST);

            return requestObjectService
                    .readRequestObject(request, context.get(CLIENT_CONTEXT_KEY), domain.useFapiBrazilProfile())
                    .map(jwt -> preserveRequestObject(context, jwt))
                    .flatMap(jwt -> validateRequestObjectClaims(context, jwt))
                    .toMaybe();
        } else {
            return Maybe.empty();
        }
    }

    /**
     * Keep the requestObject JWT into the RoutingContext for later use.
     * This is useful to retrieve parameter either from the JWT or from the request params
     *
     * @param context
     * @param jwt
     * @return
     */
    private JWT preserveRequestObject(RoutingContext context, JWT jwt) {
        context.put(REQUEST_OBJECT_KEY, jwt);
        return jwt;
    }

    private Single<JWT> validateRequestObjectClaims(RoutingContext context, JWT jwt) {
        if (this.domain.usePlainFapiProfile()) {
            try {
                final boolean fromRequestUri = context.get(REQUEST_OBJECT_FROM_URI);
                final JWTClaimsSet jwtClaimsSet = jwt.getJWTClaimsSet();

                // according to https://openid.net/specs/openid-connect-core-1_0.html#RequestObject
                // OpenID Connect request parameter values contained in the JWT supersede those passed using the OAuth 2.0 request syntax
                // but FAPI requires that these params are equals, so we test the consistency of these parameters
                // in addition FAPI requires some claims that are optional in the OIDC core spec (like exp, nbf...)
                if (jwtClaimsSet.getExpirationTime() == null || jwtClaimsSet.getExpirationTime().before(new Date())) {
                    throw generateException(jwtClaimsSet.getExpirationTime() == null && fromRequestUri, "Request object must contains valid exp claim");
                }

                List<String> redirectUri = context.queryParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI);
                final String redirectUriClaim = jwtClaimsSet.getStringClaim(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI);
                if (redirectUriClaim == null ||
                        (redirectUriClaim != null && redirectUri != null && !redirectUri.isEmpty() && !redirectUriClaim.equals(redirectUri.get(0)))) {
                    // remove redirect_uri provided as parameter and continue to let AuthorizationRequestParseParametersHandler
                    // throws the right error according to the client configuration
                    context.request().params().remove(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI);
                    throw new InvalidRequestException("Missing or invalid redirect_uri");
                }

                final Date nbf = jwtClaimsSet.getNotBeforeTime();
                if (nbf == null || (nbf.getTime() + ONE_HOUR_IN_MILLIS) < jwtClaimsSet.getExpirationTime().getTime()) {
                    throw generateException(fromRequestUri, "Request object older than 60 minutes");
                }

                List<String> state = context.queryParam(io.gravitee.am.common.oauth2.Parameters.STATE);
                final String stateClaim = jwtClaimsSet.getStringClaim(io.gravitee.am.common.oauth2.Parameters.STATE);
                if (state != null && !state.isEmpty() &&
                        (stateClaim == null || !stateClaim.equals(state.get(0)))) {
                    throw generateException(fromRequestUri, "Request object must contains valid state claim");
                }

                final OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
                if (jwtClaimsSet.getAudience() == null || (openIDProviderMetadata != null &&
                        !jwtClaimsSet.getAudience().contains(openIDProviderMetadata.getIssuer()))) {
                    // the aud claim in the request object shall be, or shall be an array containing, the OP’s Issuer Identifier URL;
                    throw generateException(fromRequestUri, "Invalid audience claim");
                }

                List<String> scope = context.queryParam(io.gravitee.am.common.oauth2.Parameters.SCOPE);
                final String scopeClaim = jwtClaimsSet.getStringClaim(Claims.scope);
                if (scope != null && !scope.isEmpty() &&
                        (scopeClaim == null || !scopeClaim.equals(scope.get(0)))) {
                    throw generateException(fromRequestUri, "Request object must contains valid scope claim");
                }

                // String scopeClaim = jwtClaimsSet.getStringClaim(Claims.scope);
                if (scopeClaim != null && scopeClaim.contains("openid") && StringUtils.isEmpty(jwtClaimsSet.getStringClaim(Parameters.NONCE))) {
                    // https://openid.net/specs/openid-financial-api-part-1-1_0-final.html#client-requesting-openid-scope
                    // If the client requests the openid scope, the authorization server shall require the nonce parameter defined
                    throw generateException(fromRequestUri, "Scope openid expect the nonce parameter defined");
                } else if ((scopeClaim == null || !scopeClaim.contains("openid")) && StringUtils.isEmpty(jwtClaimsSet.getStringClaim(io.gravitee.am.common.oauth2.Parameters.STATE))) {
                    // https://openid.net/specs/openid-financial-api-part-1-1_0-final.html#clients-not-requesting-openid-scope
                    // If the client does not request the openid scope, the authorization server shall require the state parameter defined
                    throw generateException(fromRequestUri, "Absence of scope openid expect the state parameter defined");
                }

            } catch (OAuth2Exception e) {
                return Single.error(e);
            } catch (ParseException e) {
                return Single.error(new InvalidRequestObjectException());
            }
        }

        return Single.just(jwt);
    }

    private OAuth2Exception generateException(boolean throwUriException, String msg) {
        // according to the request mode (PAR or std), FAPI error code maybe different
        return throwUriException ? new InvalidRequestUriException(msg) : new InvalidRequestObjectException(msg);
    }

    private Maybe<JWT> handleRequestObjectURI(RoutingContext context, String requestUri) {
        if (requestUri != null) {
            // Ensure that the request_uri is not propagated to the next authorization flow step
            context.request().params().remove(Parameters.REQUEST_URI);

            if (requestUri.startsWith(PushedAuthorizationRequestService.PAR_URN_PREFIX)) {
                return parService.readFromURI(requestUri, context.get(CLIENT_CONTEXT_KEY), context.get(PROVIDER_METADATA_CONTEXT_KEY))
                        .map(jwt -> preserveRequestObject(context, jwt))
                        .flatMap(jwt -> validateRequestObjectClaims(context, jwt))
                        .map(jwt -> {
                            final String uriIdentifier = requestUri.substring(PushedAuthorizationRequestService.PAR_URN_PREFIX.length());
                            context.put(REQUEST_URI_ID_KEY, uriIdentifier);
                            return jwt;
                        })
                        .toMaybe();
            } else {
                return requestObjectService
                        .readRequestObjectFromURI(requestUri, context.get(CLIENT_CONTEXT_KEY))
                        .map(jwt -> preserveRequestObject(context, jwt))
                        .flatMap(jwt -> validateRequestObjectClaims(context, jwt))
                        .toMaybe();
            }
        } else {
            return Maybe.empty();
        }
    }

    private void checkOAuthParameters(RoutingContext context, JWT jwt) {
        //So that the request is a valid OAuth 2.0 Authorization Request, values for the response_type and client_id
        // parameters MUST be included using the OAuth 2.0 request syntax, since they are REQUIRED by OAuth 2.0. The
        // values for these parameters MUST match those in the Request Object, if present.
        String clientId = context.request().getParam(io.gravitee.am.common.oauth2.Parameters.CLIENT_ID);
        String responseType = context.request().getParam(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE);

        try {
            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();

            String reqObjClientId = (String) claims.get(io.gravitee.am.common.oauth2.Parameters.CLIENT_ID);
            if (reqObjClientId != null && !reqObjClientId.equals(clientId)) {
                throw new InvalidRequestObjectException("client_id does not match request parameter");
            }

            String reqObjResponseType = (String) claims.get(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE);
            if (responseType != null && reqObjResponseType != null && !reqObjResponseType.equals(responseType)) {
                throw new InvalidRequestObjectException("response_type does not match request parameter");
            }

        } catch (ParseException pe) {
            throw new InvalidRequestObjectException();
        }
    }
}
