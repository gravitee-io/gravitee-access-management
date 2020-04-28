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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.model.oauth2.ScopeApproval;

import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequest extends OAuth2Request {

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

    /**
     * Indicates if the authorization request has been approved by the end-user (user consent)
     */
    private boolean approved;

    /**
     * OAuth 2.0 Authorization Response
     */
    private AuthorizationResponse response;

    /**
     * OpenID Connect Prompt values
     */
    private Set<String> prompts;

    /**
     * User consents if any
     */
    private List<ScopeApproval> consents;

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

    public Set<String> getPrompts() {
        return prompts;
    }

    public void setPrompts(Set<String> prompts) {
        this.prompts = prompts;
    }

    public List<ScopeApproval> getConsents() {
        return consents;
    }

    public void setConsents(List<ScopeApproval> consents) {
        this.consents = consents;
    }

    public OAuth2Request createOAuth2Request() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        // set technical information
        oAuth2Request.setId(id());
        oAuth2Request.setTransactionId(transactionId());
        oAuth2Request.setTimestamp(timestamp());
        oAuth2Request.setPath(path());
        oAuth2Request.setOrigin(getOrigin());
        oAuth2Request.setUri(uri());
        oAuth2Request.setScheme(scheme());
        oAuth2Request.setContextPath(contextPath());
        oAuth2Request.setMethod(method());
        oAuth2Request.setVersion(version());
        oAuth2Request.setHeaders(headers());
        oAuth2Request.setParameters(parameters());

        // set OAuth 2.0 information
        oAuth2Request.setClientId(getClientId());
        oAuth2Request.setScopes(getScopes());
        oAuth2Request.setResponseType(getResponseType());
        oAuth2Request.setAdditionalParameters(getAdditionalParameters());
        return oAuth2Request;
    }
}
