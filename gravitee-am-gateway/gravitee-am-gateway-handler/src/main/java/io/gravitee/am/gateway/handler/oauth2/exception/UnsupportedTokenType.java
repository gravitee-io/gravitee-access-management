package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 *
 * https://tools.ietf.org/html/rfc7009#section-2.2.1
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UnsupportedTokenType extends OAuth2Exception {

    public UnsupportedTokenType(String message) {
        super(message);
    }

    public String getOAuth2ErrorCode() {
        return "unsupported_token_type";
    }
}