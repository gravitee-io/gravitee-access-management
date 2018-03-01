package io.gravitee.am.gateway.handler.oauth2.token;

import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AccessTokenService {

    Maybe<AccessToken> get();

    Single<AccessToken> create();

    Single<AccessToken> refresh();
}
