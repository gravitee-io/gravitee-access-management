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
package io.gravitee.am.gateway.handler.oauth2.service.granter.client;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;

/**
 * Implementation of the Client Credentials Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.4"></a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsTokenGranter extends AbstractTokenGranter {

    public ClientCredentialsTokenGranter() {
        super(GrantType.CLIENT_CREDENTIALS);
        // A refresh token SHOULD NOT be included for the client client credentials flow
        // See <a href="https://tools.ietf.org/html/rfc6749#section-4.4.3">4.4.3. Access Token Response</a>
        setSupportRefreshToken(false);
    }

    public ClientCredentialsTokenGranter(TokenRequestResolver tokenRequestResolver,
                                         TokenService tokenService,
                                         RulesEngine rulesEngine) {
        this();
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setRulesEngine(rulesEngine);
    }
}
