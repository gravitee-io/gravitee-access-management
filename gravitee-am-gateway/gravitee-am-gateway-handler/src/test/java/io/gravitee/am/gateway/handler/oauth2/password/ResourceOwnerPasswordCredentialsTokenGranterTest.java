package io.gravitee.am.gateway.handler.oauth2.password;

import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.reactivex.Single;
import org.junit.Test;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceOwnerPasswordCredentialsTokenGranterTest {

    private ResourceOwnerPasswordCredentialsTokenGranter granter = new ResourceOwnerPasswordCredentialsTokenGranter();

    @Test
    public void test() {
        Single<AccessToken> accessToken = granter.grant(new TokenRequest());

    }
}
