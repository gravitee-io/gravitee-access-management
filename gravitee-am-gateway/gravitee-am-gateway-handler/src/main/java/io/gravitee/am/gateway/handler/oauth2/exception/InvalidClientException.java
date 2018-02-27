package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidClientException extends OAuth2Exception {

    public InvalidClientException(String message) {
        super(message);
    }
}
