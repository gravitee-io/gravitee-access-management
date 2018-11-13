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
package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.model.Client;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenGranter {

    /**
     * Select which OAuth 2.0 grant flow can handle the incoming Access Token Request
     * @param grantType OAuth 2.0 grant flow type
     * @return true if a grant flow can handle the Access Token Request
     */
    boolean handle(String grantType);

    /**
     * The client requests an access token by authenticating with the authorization server and presenting the authorization grant.
     * @param tokenRequest Access Token Request
     * @param client OAuth2 client
     * @return The authorization server authenticates the client and validates the authorization grant, and if valid, issues an access token.
     */
    Single<Token> grant(TokenRequest tokenRequest, Client client);
}
