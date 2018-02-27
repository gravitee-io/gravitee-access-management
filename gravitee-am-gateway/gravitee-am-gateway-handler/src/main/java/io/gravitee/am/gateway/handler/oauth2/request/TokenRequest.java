package io.gravitee.am.gateway.handler.oauth2.request;

/**
 *
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequest extends BaseRequest {

    private String grantType;
    private String username;
    private String password;

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

