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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenMinter;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TokenMinterFacade implements TokenMinter {

    private final AccessTokenMinter accessTokenMinter;

    private final IdTokenOnlyMinter idTokenOnlyMinter;

    private final IdJagTokenMinter idJagTokenMinter;

    @Override
    public Single<Token> mint(OAuth2Request oAuth2Request, Client client, User endUser) {
        // Token Exchange (RFC 8693): ID token-only mode
        // When requested_token_type is ID_TOKEN, return only the ID token in the access_token field
        TokenMinter minter = switch (oAuth2Request.getIssuedTokenType()){
            case TokenType.ID_TOKEN -> idTokenOnlyMinter;
            case TokenType.ID_JAG -> idJagTokenMinter;
            case null, default -> accessTokenMinter;
        };
        return minter.mint(oAuth2Request, client, endUser);
    }
}
