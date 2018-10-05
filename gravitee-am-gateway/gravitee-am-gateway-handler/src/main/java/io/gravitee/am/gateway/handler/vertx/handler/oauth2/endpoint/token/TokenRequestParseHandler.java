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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.token;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.TokenRequestFactory;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The authorization server validates the Access Token Request to ensure that all required parameters are present and valid.
 * If the request is valid, the authorization server authenticates the client and obtains
 * an authorization decision (by itself or by asking the resource owner or by establishing approval via other means).
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequestParseHandler implements Handler<RoutingContext> {

    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();

    @Override
    public void handle(RoutingContext context) {
        TokenRequest tokenRequest = tokenRequestFactory.create(context.request());

        // Check if a grant_type is defined
        if (tokenRequest.getGrantType() == null) {
            throw new InvalidRequestException("Missing parameter: grant_type");
        }

        // Check scope parameter
        String scopes = context.request().params().get(OAuth2Constants.SCOPE);
        if (scopes != null && scopes.isEmpty()) {
            throw new InvalidScopeException("Invalid parameter: scope must not be empty");
        }

        context.next();
    }
}
