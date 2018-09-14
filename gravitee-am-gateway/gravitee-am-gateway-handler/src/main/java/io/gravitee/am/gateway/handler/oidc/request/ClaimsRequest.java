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
package io.gravitee.am.gateway.handler.oidc.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClaimsParameter">5.5. Requesting Claims using the "claims" Request Parameter</a>
 * and <a href="https://openid.net/specs/openid-connect-core-1_0.html#IndividualClaimsRequests">5.5.1. Individual Claims Requests</a>
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
     * OPTIONAL. Indicates whether the Claim being requested is an Essential Claim.
     * If the value is true, this indicates that the Claim is an Essential Claim.
     */
    public static final String ESSENTIAL = "essential";
    /**
     * OPTIONAL. Requests that the Claim be returned with a particular value
     */
    public static final String VALUE = "value";
    /**
     * OPTIONAL. Requests that the Claim be returned with one of a set of values, with the values appearing in order of preference.œ
     */
    public static final String VALUES = "values";
    /**
     * Individual Claims to be returned from the UserInfo Endpoint.
     */
    @JsonProperty("userinfo")
    private Map<String, Object> userInfoClaims;
    /**
     * Individual Claims to be returned in the ID Token.
     */
    @JsonProperty("id_token")
    private Map<String, Object> idTokenClaims;

    public Map<String, Object> getUserInfoClaims() {
        return userInfoClaims;
    }

    public void setUserInfoClaims(Map<String, Object> userInfoClaims) {
        this.userInfoClaims = userInfoClaims;
    }

    public Map<String, Object> getIdTokenClaims() {
        return idTokenClaims;
    }

    public void setIdTokenClaims(Map<String, Object> idTokenClaims) {
        this.idTokenClaims = idTokenClaims;
    }

    public static class Essential {
        private boolean essential;

        public Essential() {
        }

        public Essential(boolean essential) {
            this.essential = essential;
        }

        public boolean isEssential() {
            return essential;
        }

        public void setEssential(boolean essential) {
            this.essential = essential;
        }
    }
}
