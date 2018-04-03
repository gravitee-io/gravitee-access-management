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
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.AccessTokenService;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.reactivex.functions.Function;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessTokenServiceImpl implements AccessTokenService {

    private TokenRepository tokenRepository;

    @Override
    public Maybe<AccessToken> get() {
        return tokenRepository.getAccessToken("authentication-key")
                .flatMap(new Function<OAuth2AccessToken, MaybeSource<? extends AccessToken>>() {
                    @Override
                    public MaybeSource<? extends AccessToken> apply(OAuth2AccessToken oAuth2AccessToken) throws Exception {
                        return Maybe.just(new AccessToken() {});
                    }
                });
    }

    @Override
    public Single<AccessToken> create() {
        return null;
    }

    @Override
    public Single<AccessToken> refresh() {
        return null;
    }
}
