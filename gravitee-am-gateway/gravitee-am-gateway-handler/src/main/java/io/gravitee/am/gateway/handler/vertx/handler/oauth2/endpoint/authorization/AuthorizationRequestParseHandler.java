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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.LoginRequiredException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.AuthorizationRequestFactory;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.List;

/**
 * The authorization server validates the request to ensure that all required parameters are present and valid.
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseHandler implements Handler<RoutingContext> {

    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();

    @Override
    public void handle(RoutingContext context) {
        AuthorizationRequest authorizationRequest = authorizationRequestFactory.create(context.request());

        // The authorization server validates the request to ensure that all required parameters are present and valid.
        String responseType = authorizationRequest.getResponseType();
        String clientId = authorizationRequest.getClientId();

        if (responseType == null || (!responseType.equals(OAuth2Constants.TOKEN) && !responseType.equals(OAuth2Constants.CODE))) {
            throw new UnsupportedResponseTypeException("Unsupported response type: " + responseType);
        }

        if (clientId == null) {
            throw new InvalidRequestException("A client id is required");
        }

        // proceed prompt parameter
        parsePromptParameter(context);

        context.next();
    }

    protected final void parsePromptParameter(RoutingContext context) {
        String prompt = context.request().getParam(OIDCParameters.PROMPT);

        if (prompt != null) {
            // retrieve prompt values (prompt parameter is a space delimited, case sensitive list of ASCII string values)
            // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
            List<String> promptValues = Arrays.asList(prompt.split("\\s+"));

            // The Authorization Server MUST NOT display any authentication or consent user interface pages.
            // An error is returned if an End-User is not already authenticated.
            if (promptValues.contains("none") && context.user() == null) {
                throw new LoginRequiredException();
            }
        }
    }

    public static AuthorizationRequestParseHandler create() {
        return new AuthorizationRequestParseHandler();
    }
}
