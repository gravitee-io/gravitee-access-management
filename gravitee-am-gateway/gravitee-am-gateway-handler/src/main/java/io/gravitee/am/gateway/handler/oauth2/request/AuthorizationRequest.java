package io.gravitee.am.gateway.handler.oauth2.request;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequest extends BaseRequest {

    /**
     * REQUIRED
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1.1"></a>
     */
    private String responseType;

    /**
     * OPTIONAL
     *
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

    /**
     * RECOMMENDED
     *
     * An opaque value used by the client to maintain
     * state between the request and callback.  The authorization
     * server includes this value when redirecting the user-agent back
     * to the client.  The parameter SHOULD be used for preventing
     * cross-site request forgery as described in Section 10.12.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-10.12"></a>
     */
    private String state;

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
