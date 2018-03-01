package io.gravitee.am.gateway.handler.oauth2.exception;

import io.gravitee.common.http.HttpStatusCode;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BadClientCredentialsException extends OAuth2Exception {

    public BadClientCredentialsException() {
        super("Bad client credentials");
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_client";
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.UNAUTHORIZED_401;
    }
}
