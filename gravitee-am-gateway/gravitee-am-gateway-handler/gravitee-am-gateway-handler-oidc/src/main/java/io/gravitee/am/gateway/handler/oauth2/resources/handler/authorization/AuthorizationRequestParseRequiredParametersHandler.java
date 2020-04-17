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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseModeException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.List;
import java.util.Set;

import static io.gravitee.am.service.utils.ResponseTypeUtils.requireNonce;

/**
 * The authorization server validates the request to ensure that all required parameters are present and valid.
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 *
 * OIDC Certification seems to make a difference between required and optional parameters.
 * Missing required parameters should result in the OpenID Provider displaying an error message in your user agent.
 * You must submit a screen shot of the error shown as part of your certification application.
 *
 * We don't how oauth2 clients use error messages, we must use our default error page to handle missing required parameters (i.e call this handler before client handler).
 *
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseRequiredParametersHandler implements Handler<RoutingContext> {

    private OpenIDDiscoveryService openIDDiscoveryService;

    public AuthorizationRequestParseRequiredParametersHandler(OpenIDDiscoveryService openIDDiscoveryService) {
        this.openIDDiscoveryService = openIDDiscoveryService;
    }

    @Override
    public void handle(RoutingContext context) {
        // proceed request parameters
        parseRequestParameters(context);

        // proceed response type parameter
        parseResponseTypeParameter(context);

        // proceed response mode parameter
        parseResponseModeParameter(context);

        // proceed client_id parameter
        parseClientIdParameter(context);

        // proceed nonce parameter
        parseNonceParameter(context);

        context.next();
    }

    private void parseRequestParameters(RoutingContext context) {
        // invalid_request if the request is missing a required parameter, includes an
        // invalid parameter value, includes a parameter more than once, or is otherwise malformed.
        MultiMap requestParameters = context.request().params();
        Set<String> requestParametersNames = requestParameters.names();
        requestParametersNames.forEach(requestParameterName -> {
            List<String> requestParameterValue = requestParameters.getAll(requestParameterName);
            if (requestParameterValue.size() > 1) {
                throw new InvalidRequestException("Parameter [" + requestParameterName + "] is included more than once");
            }
        });
    }

    private void parseResponseTypeParameter(RoutingContext context) {
        String responseType = context.request().getParam(Parameters.RESPONSE_TYPE);

        if (responseType == null) {
            throw new InvalidRequestException("Missing parameter: response_type");
        }

        // get supported response types
        List<String> responseTypesSupported = openIDDiscoveryService.getConfiguration("/").getResponseTypesSupported();
        if (!responseTypesSupported.contains(responseType)) {
            throw new UnsupportedResponseTypeException("Unsupported response type: " + responseType);
        }
    }

    private void parseResponseModeParameter(RoutingContext context) {
        String responseMode = context.request().getParam(Parameters.RESPONSE_MODE);

        if (responseMode == null) {
            return;
        }

        // get supported response modes
        List<String> responseModesSupported = openIDDiscoveryService.getConfiguration("/").getResponseModesSupported();
        if (!responseModesSupported.contains(responseMode)) {
            throw new UnsupportedResponseModeException("Unsupported response mode: " + responseMode);
        }
    }

    private void parseClientIdParameter(RoutingContext context) {
        String clientId = context.request().getParam(Parameters.CLIENT_ID);

        if (clientId == null) {
            throw new InvalidRequestException("Missing parameter: client_id");
        }
    }

    private void parseNonceParameter(RoutingContext context) {
        String nonce = context.request().getParam(io.gravitee.am.common.oidc.Parameters.NONCE);
        String responseType = context.request().getParam(Parameters.RESPONSE_TYPE);
        // nonce parameter is required for the Hybrid flow
        if (nonce == null && requireNonce(responseType)) {
            throw new InvalidRequestException("Missing parameter: nonce is required for Implicit and Hybrid Flow");
        }
    }
}
