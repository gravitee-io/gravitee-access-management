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
package io.gravitee.am.gateway.handler.oauth2.granter.implicit;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.reactivex.Single;

/**
 * Implementation of the Implicit Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2"></a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ImplicitTokenGranter extends AbstractTokenGranter {


    private final static String GRANT_TYPE = "implicit";

    public ImplicitTokenGranter() {
        super(GRANT_TYPE);
    }

    public ImplicitTokenGranter(ClientService clientService, TokenService tokenService) {
        this();
        setClientService(clientService);
        setTokenService(tokenService);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return super.grant(tokenRequest);
    }
}
