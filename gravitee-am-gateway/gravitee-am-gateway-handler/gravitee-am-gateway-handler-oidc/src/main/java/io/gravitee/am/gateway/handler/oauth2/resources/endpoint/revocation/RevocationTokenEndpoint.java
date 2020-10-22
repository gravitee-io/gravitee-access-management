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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.revocation;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.RevocationTokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.RevocationTokenService;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0 Token Revocation
 *
 * See <a href="https://tools.ietf.org/html/rfc7009">OAuth 2.0 Token Revocation</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RevocationTokenEndpoint implements Handler<RoutingContext> {

    private final static Logger logger = LoggerFactory.getLogger(RevocationTokenEndpoint.class);
    private RevocationTokenService revocationTokenService;

    public RevocationTokenEndpoint() {
    }

    public RevocationTokenEndpoint(RevocationTokenService revocationTokenService) {
        this.revocationTokenService = revocationTokenService;
    }

    @Override
    public void handle(RoutingContext context) {
        // The authorization server first validates the client credentials (in
        // case of a confidential client) and then verifies whether the token
        // was issued to the client making the revocation request.  If this
        // validation fails, the request is refused and the client is informed
        // of the error by the authorization server as described below.
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (client == null) {
            throw new InvalidClientException();
        }

        revocationTokenService
                .revoke(createRequest(context), client)
                .subscribe(() -> context.response().setStatusCode(200).end(), context::fail);
    }

    private static RevocationTokenRequest createRequest(RoutingContext context) {
        String token = context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY);
        String tokenTypeHint = context.request().getParam(ConstantKeys.TOKEN_TYPE_HINT_PARAM_KEY);

        if (token == null) {
            throw new InvalidRequestException();
        }

        RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest(token);

        if (tokenTypeHint != null) {
            try {
                revocationTokenRequest.setHint(TokenTypeHint.from(tokenTypeHint));
            } catch (IllegalArgumentException iae) {
                // If the server is unable to locate the token using the given hint,
                // it MUST extend its search across all of its supported token types.
                logger.debug("Invalid token type hint : " + tokenTypeHint);
            }
        }

        return revocationTokenRequest;
    }
}
