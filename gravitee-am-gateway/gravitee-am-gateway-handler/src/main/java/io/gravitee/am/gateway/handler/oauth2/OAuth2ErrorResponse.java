package io.gravitee.am.gateway.handler.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * See <a href="https://tools.ietf.org/html/rfc6749#section-5.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ErrorResponse {

    @JsonProperty("error")
    private final String code;

    @JsonProperty("error_description")
    private String description;

    @JsonProperty("error_uri")
    private String uri;

    public OAuth2ErrorResponse(String errorCode) {
        this.code = errorCode;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
