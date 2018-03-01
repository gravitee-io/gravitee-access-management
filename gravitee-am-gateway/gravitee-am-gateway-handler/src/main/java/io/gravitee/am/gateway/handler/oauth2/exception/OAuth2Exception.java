package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class OAuth2Exception extends RuntimeException {

    public OAuth2Exception() {
        super();
    }

    public OAuth2Exception(String message) {
        super(message);
    }

    public int getHttpStatusCode() {
        return 400;
    }

    public String getOAuth2ErrorCode() {
        return "invalid_request";
    }
}
