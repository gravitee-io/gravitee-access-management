package io.gravitee.am.gateway.handler.oauth2.exception;

import io.gravitee.common.http.HttpStatusCode;

/**
 * Client authentication failed (e.g., unknown client,
 * no client authentication included, or unsupported
 * authentication method).  The authorization server MAY
 * return an HTTP 401 (Unauthorized) status code to indicate
 * which HTTP authentication schemes are supported.  If the
 * client attempted to authenticate via the "Authorization"
 * request header field, the authorization server MUST
 * respond with an HTTP 401 (Unauthorized) status code and
 * include the "WWW-Authenticate" response header field
 * matching the authentication scheme used by the client.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-5.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidClientException extends OAuth2Exception {

    public InvalidClientException() {
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
