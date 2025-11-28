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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenService {

    Maybe<Token> getAccessToken(String accessToken, Client client);

    Maybe<Token> getRefreshToken(String refreshToken, Client client);

    default Maybe<Token> introspect(String token) {
        return introspect(token, (String) null);
    }

    default Maybe<Token> introspect(String token, TokenTypeHint hint) {
        return introspect(token, hint, null);
    }

    Maybe<Token> introspect(String token, String callerClientId);

    Maybe<Token> introspect(String token, TokenTypeHint hint, String callerClientId);

    Single<Token> create(OAuth2Request oAuth2Request, Client client, User endUser);

    Single<Token> refresh(String refreshToken, TokenRequest tokenRequest, Client client);

    Completable deleteAccessToken(String accessToken);

    Completable deleteRefreshToken(String refreshToken);
}
