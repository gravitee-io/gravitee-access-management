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
