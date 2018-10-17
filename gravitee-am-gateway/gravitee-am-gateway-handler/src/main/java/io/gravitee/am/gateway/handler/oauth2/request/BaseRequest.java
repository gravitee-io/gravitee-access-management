/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public MultiValueMap<String, String> getRequestParameters() {
        return requestParameters;
    }

    public void setRequestParameters(MultiValueMap<String, String> requestParameters) {
        this.requestParameters = requestParameters;
    }

    public MultiValueMap<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(MultiValueMap<String, String> additionalParameters) {
        this.additionalParameters = additionalParameters;
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
