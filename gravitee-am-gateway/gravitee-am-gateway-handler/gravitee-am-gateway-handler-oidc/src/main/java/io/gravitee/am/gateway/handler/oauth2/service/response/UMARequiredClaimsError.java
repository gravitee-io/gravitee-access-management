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
package io.gravitee.am.gateway.handler.oauth2.service.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.common.exception.uma.RequiredClaims;

import java.util.List;

/**
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#authorization-failure"></a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UMARequiredClaimsError {

    @JsonProperty("claim_token_format")
    private List<String> claimTokenFormat;

    @JsonProperty("claim_yype")
    private String claimType;

    @JsonProperty("friendly_name")
    private String friendlyName;

    @JsonProperty("issuer")
    private String issuer;

    @JsonProperty("name")
    private String name;

    public UMARequiredClaimsError(String name) {
        this.name = name;
    }

    public UMARequiredClaimsError setClaimTokenFormat(List<String> claimTokenFormat) {
        this.claimTokenFormat = claimTokenFormat;
        return this;
    }

    public UMARequiredClaimsError setClaimType(String claimType) {
        this.claimType = claimType;
        return this;
    }

    public UMARequiredClaimsError setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        return this;
    }

    public UMARequiredClaimsError setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public static UMARequiredClaimsError from(RequiredClaims requiredClaims) {
        return new UMARequiredClaimsError(requiredClaims.getName())
                .setFriendlyName(requiredClaims.getFriendlyName())
                .setClaimType(requiredClaims.getClaimType())
                .setIssuer(requiredClaims.getIssuer())
                .setClaimTokenFormat(requiredClaims.getClaimTokenFormat());
    }
}
