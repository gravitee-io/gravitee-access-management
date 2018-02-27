package io.gravitee.am.gateway.handler.oauth2.request;

import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class BaseRequest {

    /**
     * The authorization server issues the registered client a client
     * identifier -- a unique string representing the registration
     * information provided by the client.  The client identifier is not a
     * secret; it is exposed to the resource owner and MUST NOT be used
     * alone for client authentication.  The client identifier is unique to
     * the authorization server.
     *
     * The client identifier string size is left undefined by this
     * specification.  The client should avoid making assumptions about the
     * identifier size.  The authorization server SHOULD document the size
     * of any identifier it issues.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-2.2"></a>
     */
    private String clientId;

    private Set<String> scopes = new HashSet<>();

    private MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public void setRequestParameters(MultiValueMap<String, String> requestParameters) {
        this.requestParameters = requestParameters;
    }

    public String getClientId() {
        return clientId;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public MultiValueMap<String, String> getRequestParameters() {
        return requestParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseRequest that = (BaseRequest) o;

        if (clientId != null ? !clientId.equals(that.clientId) : that.clientId != null) return false;
        if (scopes != null ? !scopes.equals(that.scopes) : that.scopes != null) return false;
        return requestParameters != null ? requestParameters.equals(that.requestParameters) : that.requestParameters == null;
    }

    @Override
    public int hashCode() {
        int result = clientId != null ? clientId.hashCode() : 0;
        result = 31 * result + (scopes != null ? scopes.hashCode() : 0);
        result = 31 * result + (requestParameters != null ? requestParameters.hashCode() : 0);
        return result;
    }
}
