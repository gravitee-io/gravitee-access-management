package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UnauthorizedClientException extends OAuth2Exception {

    public UnauthorizedClientException(String message) {
        super(message);
    }
}
