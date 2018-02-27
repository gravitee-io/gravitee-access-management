package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenGranter {

    Single<AccessToken> grant(TokenRequest tokenRequest);
}
