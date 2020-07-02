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
package io.gravitee.am.gateway.handler.oidc.service.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClaimsParameter">5.5. Requesting Claims using the "claims" Request Parameter</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimsRequest {

    /**
     * OPTIONAL. Requests that the listed individual Claims be returned from the UserInfo Endpoint.
     * If present, the listed Claims are being requested to be added to any Claims that are being requested using scope values.
     * If not present, the Claims being requested from the UserInfo Endpoint are only those requested using scope values.
     * When the userinfo member is used, the request MUST also use a response_type value that results in an Access Token being issued to the Client for use at the UserInfo Endpoint.
     */
    public static final String USERINFO = "userinfo";
    /**
     * OPTIONAL. Requests that the listed individual Claims be returned in the ID Token.
     * If present, the listed Claims are being requested to be added to the default Claims in the ID Token.
     * If not present, the default ID Token Claims are requested, as per the ID Token definition in Section 2 and per the additional per-flow ID Token requirements in Sections 3.1.3.6, 3.2.2.10, 3.3.2.11, and 3.3.3.6.
     */
    public static final String ID_TOKEN = "id_token";
    /**
     * Individual Claims to be returned from the UserInfo Endpoint.
     */
    @JsonProperty("userinfo")
    private Map<String, ClaimRequest> userInfoClaims;
    /**
     * Individual Claims to be returned in the ID Token.
     */
    @JsonProperty("id_token")
    private Map<String, ClaimRequest> idTokenClaims;

    public Map<String, ClaimRequest> getUserInfoClaims() {
        return userInfoClaims;
    }

    public void setUserInfoClaims(Map<String, ClaimRequest> userInfoClaims) {
        this.userInfoClaims = userInfoClaims;
    }

    public Map<String, ClaimRequest> getIdTokenClaims() {
        return idTokenClaims;
    }

    public void setIdTokenClaims(Map<String, ClaimRequest> idTokenClaims) {
        this.idTokenClaims = idTokenClaims;
    }
}
