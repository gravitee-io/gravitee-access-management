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
package io.gravitee.am.gateway.handler.oauth2.service.granter;

import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Response;
import io.reactivex.rxjava3.core.Single;

/**
 * An authorization grant is a credential representing the resource
 *    owner's authorization (to access its protected resources) used by the
 *    client to obtain an access token.  This specification defines four
 *    grant types -- authorization code, implicit, resource owner password
 *    credentials, and client credentials -- as well as an extensibility
 *    mechanism for defining additional types.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-1.3">1.3. Authorization Grant</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenGranter {

    /**
     * Select which OAuth 2.0 grant flow can handle the incoming Access Token Request
     * @param grantType OAuth 2.0 grant flow type
     * @param client OAuth 2.0 client
     * @return true if a grant flow can handle the Access Token Request
     */
    boolean handle(String grantType, Client client);

    /**
     * The client requests an access token by authenticating with the authorization server and presenting the authorization grant.
     * @param tokenRequest Access Token Request
     * @param client OAuth2 client
     * @return The authorization server authenticates the client and validates the authorization grant, and if valid, issues an access token.
     */
    Single<Token> grant(TokenRequest tokenRequest, Client client);


     Single<Token> grant(TokenRequest tokenRequest, Response response, Client client);

    default boolean handle(String grantType) {
        return handle(grantType, null);
    }
}
