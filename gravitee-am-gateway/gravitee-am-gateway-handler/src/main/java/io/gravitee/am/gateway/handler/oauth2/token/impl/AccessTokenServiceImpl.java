package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.AccessTokenService;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessTokenServiceImpl implements AccessTokenService {

    @Override
    public Single<AccessToken> get() {
        return null;
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
