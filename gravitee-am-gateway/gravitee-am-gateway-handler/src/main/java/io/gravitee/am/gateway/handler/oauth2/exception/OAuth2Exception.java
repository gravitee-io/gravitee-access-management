package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Exception extends RuntimeException {

    public OAuth2Exception() {
        super();
    }

    public OAuth2Exception(String message) {
        super(message);
    }
}
