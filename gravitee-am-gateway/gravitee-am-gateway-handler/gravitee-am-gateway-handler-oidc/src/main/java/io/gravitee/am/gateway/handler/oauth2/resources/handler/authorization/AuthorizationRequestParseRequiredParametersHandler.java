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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.List;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY;

/**
 * The authorization server validates the request to ensure that all required parameters are present and valid.
 *
 * response_type and client_id parameters MUST be included using the OAuth 2.0 request syntax, since they are REQUIRED by OAuth 2.0.
 *
 * OIDC Certification seems to make a difference between required and optional parameters.
 * Missing required parameters should result in the OpenID Provider displaying an error message in your user agent.
 * You must submit a screen shot of the error shown as part of your certification application.
 *
 * We don't how oauth2 clients use error messages, we must use our default error page to handle missing required parameters (i.e call this handler before client handler).
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseRequiredParametersHandler extends AbstractAuthorizationRequestHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        // proceed request parameters
        parseRequestParameters(context);

        // proceed response type parameter
        parseResponseTypeParameter(context);

        // proceed client_id parameter
        parseClientIdParameter(context);

        context.next();
    }

    private void parseRequestParameters(RoutingContext context) {
        // invalid_request if the request is missing a required parameter, includes an
        // invalid parameter value, includes a parameter more than once, or is otherwise malformed.
        MultiMap requestParameters = context.request().params();
        Set<String> requestParametersNames = requestParameters.names();
        requestParametersNames.forEach(requestParameterName -> {
            List<String> requestParameterValue = requestParameters.getAll(requestParameterName);
            if (requestParameterValue.size() > 1 && !requestParameterName.equals(Parameters.RESOURCE)) {
                throw new InvalidRequestException("Parameter [" + requestParameterName + "] is included more than once");
            }
        });
    }

    private void parseResponseTypeParameter(RoutingContext context) {
        String responseType = context.request().getParam(Parameters.RESPONSE_TYPE);
        OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);

        if (!isJwtAuthRequest(context)) {
            // for non JAR request, response_type is required as query parameter
            // otherwise, it can be provided by the request object and will be checked
            // later in the flow by io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseParametersHandler
            checkResponseType(responseType, openIDProviderMetadata);
        }
    }

    private void parseClientIdParameter(RoutingContext context) {
        String clientId = context.request().getParam(Parameters.CLIENT_ID);

        if (clientId == null) {
            throw new InvalidRequestException("Missing parameter: " + Parameters.CLIENT_ID);
        }
    }
}
