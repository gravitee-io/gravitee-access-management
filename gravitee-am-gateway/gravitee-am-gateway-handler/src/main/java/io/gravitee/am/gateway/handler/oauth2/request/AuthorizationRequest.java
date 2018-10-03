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

import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;

import java.io.Serializable;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequest extends BaseRequest implements Serializable {

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

    private boolean approved;

    private AuthorizationResponse response;

    private Map<String, String> approvalParameters;

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

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public AuthorizationResponse getResponse() {
        return response;
    }

    public void setResponse(AuthorizationResponse response) {
        this.response = response;
    }

    public Map<String, String> getApprovalParameters() {
        return approvalParameters;
    }

    public void setApprovalParameters(Map<String, String> approvalParameters) {
        this.approvalParameters = approvalParameters;
    }

    public OAuth2Request createOAuth2Request() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId(getClientId());
        oAuth2Request.setScopes(getScopes());
        oAuth2Request.setRequestParameters(getRequestParameters());
        oAuth2Request.setResponseType(getResponseType());
        return oAuth2Request;
    }
}
