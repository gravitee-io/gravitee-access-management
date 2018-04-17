package io.gravitee.am.gateway.handler.oauth2.token;

/**
 *
 * See <a href="https://tools.ietf.org/html/rfc7009#section-2.1"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum TokenTypeHint {

    ACCESS_TOKEN,
    REFRESH_TOKEN;

    public static TokenTypeHint from(String name) throws IllegalArgumentException {
        return TokenTypeHint.valueOf(name.toUpperCase());
    }
}
