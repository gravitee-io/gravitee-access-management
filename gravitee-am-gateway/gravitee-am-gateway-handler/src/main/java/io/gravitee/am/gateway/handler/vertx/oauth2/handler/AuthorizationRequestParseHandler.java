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
package io.gravitee.am.gateway.handler.vertx.oauth2.handler;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.oauth2.request.AuthorizationRequestFactory;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

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

        context.next();
    }

    public static AuthorizationRequestParseHandler create() {
        return new AuthorizationRequestParseHandler();
    }
}
