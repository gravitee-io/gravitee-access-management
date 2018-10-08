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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.TokenRequestFactory;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The token endpoint is used by the client to obtain an access token by presenting its authorization grant or refresh token.
 * The token endpoint is used with every authorization grant except for the implicit grant type (since an access token is issued directly).
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEndpointHandler implements Handler<RoutingContext> {

    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();
    private TokenGranter tokenGranter;

    public TokenEndpointHandler() { }

    public TokenEndpointHandler(TokenGranter tokenGranter) {
        this.tokenGranter = tokenGranter;
    }

    @Override
    public void handle(RoutingContext context) {
        TokenRequest tokenRequest = tokenRequestFactory.create(context.request());

        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof Client)) {
            throw new InvalidClientException();
        }

        final io.gravitee.am.model.Client client = ((Client) authenticatedUser.getDelegate()).getClient();

        // Check that authenticated user is matching the client_id
        // client_id is not required in the token request since the client can be authenticated via a Basic Authentication
        if (tokenRequest.getClientId() != null) {
            if (!client.getClientId().equals(tokenRequest.getClientId())) {
                throw new InvalidClientException();
            }
        } else {
            // set token request client_id with the authenticated client
            tokenRequest.setClientId(client.getClientId());
        }

        // check if client has authorized grant types
        if (client.getAuthorizedGrantTypes() == null || client.getAuthorizedGrantTypes().isEmpty()) {
            throw new InvalidClientException("Invalid client: client must at least have one grant type configured");
        }

        tokenGranter.grant(tokenRequest, client)
                .subscribe(accessToken -> context.response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .end(Json.encodePrettily(accessToken))
                        , error -> context.fail(error));
    }
}
