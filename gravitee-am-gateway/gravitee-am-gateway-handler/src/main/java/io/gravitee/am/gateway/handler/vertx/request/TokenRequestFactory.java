package io.gravitee.am.gateway.handler.vertx.request;

import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.vertx.core.http.HttpServerRequest;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class TokenRequestFactory {

    public TokenRequest create(HttpServerRequest request) {
        TokenRequest tokenRequest = new TokenRequest();

        tokenRequest.setClientId(request.params().get(OAuth2Constants.CLIENT_ID));
        tokenRequest.setGrantType(request.params().get(OAuth2Constants.GRANT_TYPE));

        return tokenRequest;
    }
}
