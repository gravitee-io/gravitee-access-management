package io.gravitee.am.gateway.handler.oauth2.code;

import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.reactivex.Single;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#page-24"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "authorization_code";

    public AuthorizationCodeTokenGranter() {
        super(GRANT_TYPE);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return super.grant(tokenRequest);
    }
}
