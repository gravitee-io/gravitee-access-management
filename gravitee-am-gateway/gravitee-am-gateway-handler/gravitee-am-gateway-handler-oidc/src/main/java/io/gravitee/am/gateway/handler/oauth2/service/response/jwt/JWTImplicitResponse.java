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
package io.gravitee.am.gateway.handler.oauth2.service.response.jwt;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.oauth2.service.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;

/**
 * See <a href="https://openid.net//specs/openid-financial-api-jarm.html#response-type-code">4.1.1.  Response Type "code"</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWTImplicitResponse extends JWTAuthorizationResponse<ImplicitResponse> {

    public JWTImplicitResponse(final ImplicitResponse response) {
        super(response);
    }

    @Override
    public JWT build() {
        JWT jwt = super.build();

        Token accessToken = response.getAccessToken();

        jwt.put(Token.ACCESS_TOKEN, accessToken.getValue());
        jwt.put(Token.TOKEN_TYPE, accessToken.getTokenType());
        jwt.put(Token.EXPIRES_IN, String.valueOf(accessToken.getExpiresIn()));

        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            jwt.put(Token.SCOPE, accessToken.getScope());
        }

        // additional information
        if (accessToken.getAdditionalInformation() != null) {
            accessToken.getAdditionalInformation().forEach((k, v) -> jwt.put(k, String.valueOf(v)));
        }

        return jwt;
    }
}
