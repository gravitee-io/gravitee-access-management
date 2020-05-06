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
package io.gravitee.am.gateway.handler.oidc.resources.handler.authorization;

import com.nimbusds.jwt.JWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
public class AuthorizationRequestParseRequestObjectHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";

    private static final String HTTPS_SCHEME  = "https";

    // As per https://openid.net/specs/openid-connect-core-1_0.html#AuthRequests
    private static final List<String> OVERRIDABLE_PARAMETERS = Arrays.asList(
            Parameters.CLAIMS,
            Parameters.MAX_AGE,
            io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI,
            io.gravitee.am.common.oauth2.Parameters.SCOPE,
            io.gravitee.am.common.oauth2.Parameters.RESPONSE_MODE,
            Parameters.DISPLAY,
            Parameters.PROMPT,
            Parameters.UI_LOCALES,
            Parameters.ID_TOKEN_HINT,
            Parameters.LOGIN_HINT,
            Parameters.ACR_VALUES);

    private RequestObjectService requestObjectService;

    public AuthorizationRequestParseRequestObjectHandler(RequestObjectService requestObjectService) {
        this.requestObjectService = requestObjectService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Even if a scope parameter is present in the Request Object value, a scope parameter MUST always be passed
        // using the OAuth 2.0 request syntax containing the openid scope value to indicate to the underlying OAuth 2.0
        // logic that this is an OpenID Connect request.
        String scope = context.request().getParam(io.gravitee.am.common.oauth2.Parameters.SCOPE);
        HashSet<String> scopes = scope != null && !scope.isEmpty() ? new HashSet<>(Arrays.asList(scope.split("\\s+"))) : null;
        if (scopes == null || !scopes.contains(Scope.OPENID.getKey())) {
            context.next();
            return;
        }

        // if there is no request or request_uri parameters, continue
        if ((context.request().getParam(Parameters.REQUEST) == null || context.request().getParam(Parameters.REQUEST).isEmpty())
                && ((context.request().getParam(Parameters.REQUEST_URI) == null || context.request().getParam(Parameters.REQUEST_URI).isEmpty()))) {
            context.next();
            return;
        }

        // check request object parameters
        checkRequestObjectParameters(context);

        // Proceed request and request_uri parameters
        Maybe<JWT> requestObject = null;

        if (context.request().getParam(Parameters.REQUEST) != null) {
            requestObject = handleRequestObjectValue(context);
        } else if (context.request().getParam(Parameters.REQUEST_URI) != null) {
            requestObject = handleRequestObjectURI(context);
        }

        requestObject
                .subscribe(
                        jwt -> {
                            try {
                                // Check OAuth2 parameters
                                checkOAuthParameters(context, jwt);
                                overrideRequestParameters(context, jwt);
                                context.next();
                            } catch (Exception ex) {
                                context.fail(ex);
                            }
                        },
                        context::fail,
                        () -> context.next());
    }

    private void checkRequestObjectParameters(RoutingContext context) {
        // Requests using these parameters are represented as JWTs, which are respectively passed by value or by
        // reference. The ability to pass requests by reference is particularly useful for large requests. If one of
        // these parameters is used, the other MUST NOT be used in the same request.
        String request = context.request().getParam(Parameters.REQUEST);
        String requestUri = context.request().getParam(Parameters.REQUEST_URI);

        if (request != null && requestUri != null && !request.isEmpty() && !requestUri.isEmpty()) {
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
                if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase(HTTPS_SCHEME) && !requestUri.startsWith(RequestObjectService.RESOURCE_OBJECT_URN_PREFIX))) {
                    throw new InvalidRequestUriException("request_uri parameter scheme must be HTTPS");
                }
            } catch (IllegalArgumentException iae) {
                throw new InvalidRequestUriException("request_uri parameter is not valid");
            }
        }
    }

    private Maybe<JWT> handleRequestObjectValue(RoutingContext context) {
        final String request = context.request().getParam(Parameters.REQUEST);

        if (request != null) {
            // Ensure that the request_uri is not propagated to the next authorization flow step
            context.request().params().remove(Parameters.REQUEST);

            return requestObjectService
                    .readRequestObject(request, context.get(CLIENT_CONTEXT_KEY))
                    .toMaybe();
        } else {
            return Maybe.empty();
        }
    }

    private Maybe<JWT> handleRequestObjectURI(RoutingContext context) {
        final String requestUri = context.request().getParam(Parameters.REQUEST_URI);

        if (requestUri != null) {
            // Ensure that the request_uri is not propagated to the next authorization flow step
            context.request().params().remove(Parameters.REQUEST_URI);

            return requestObjectService
                    .readRequestObjectFromURI(requestUri, context.get(CLIENT_CONTEXT_KEY))
                    .toMaybe();
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
            if (reqObjResponseType != null && !reqObjResponseType.equals(responseType)) {
                throw new InvalidRequestObjectException("response_type does not match request parameter");
            }

        } catch (ParseException pe) {
            throw new InvalidRequestObjectException();
        }
    }

    private void overrideRequestParameters(RoutingContext context, JWT jwt) {
        try {
            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();

            OVERRIDABLE_PARAMETERS
                    .forEach(key -> {
                        Object property = claims.get(key);
                        if (property != null) {
                            context.request().params().set(key, property.toString());
                        }
                    });
        } catch (ParseException pe) {
            throw new InvalidRequestObjectException();
        }
    }
}
