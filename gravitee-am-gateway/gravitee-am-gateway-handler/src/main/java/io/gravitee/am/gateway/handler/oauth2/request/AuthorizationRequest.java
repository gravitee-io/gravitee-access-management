package io.gravitee.am.gateway.handler.oauth2.request;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequest extends BaseRequest {

    private String responseType;

    /**
     * After completing its interaction with the resource owner, the
     * authorization server directs the resource owner's user-agent back to
     * the client.  The authorization server redirects the user-agent to the
     * client's redirection endpoint previously established with the
     * authorization server during the client registration process or when
     * making the authorization request.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1.2"></a>
     */
    private String redirectUri;
}
