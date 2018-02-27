package io.gravitee.am.gateway.handler.oauth2.password;

import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.reactivex.Single;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceOwnerPasswordCredentialsTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "password";

    public ResourceOwnerPasswordCredentialsTokenGranter() {
        super(GRANT_TYPE);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return super.grant(tokenRequest);
    }
}
