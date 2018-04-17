package io.gravitee.am.gateway.handler.oauth2.introspection;

import io.gravitee.am.gateway.handler.oauth2.token.TokenTypeHint;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionRequest {

    private final String token;

    private TokenTypeHint hint;

    public IntrospectionRequest(final String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public TokenTypeHint getHint() {
        return hint;
    }

    public void setHint(TokenTypeHint hint) {
        this.hint = hint;
    }
}
