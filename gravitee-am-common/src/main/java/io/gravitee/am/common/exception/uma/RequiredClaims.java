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
package io.gravitee.am.common.exception.uma;

import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class RequiredClaims {

    private List<String> claimTokenFormat;
    private String claimType;
    private String friendlyName;
    private String issuer;
    private String name;

    public RequiredClaims(String name) {
        this.name = name;
    }

    public List<String> getClaimTokenFormat() {
        return claimTokenFormat;
    }

    public RequiredClaims setClaimTokenFormat(List<String> claimTokenFormat) {
        this.claimTokenFormat = claimTokenFormat;
        return this;
    }

    public String getClaimType() {
        return claimType;
    }

    public RequiredClaims setClaimType(String claimType) {
        this.claimType = claimType;
        return this;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public RequiredClaims setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        return this;
    }

    public String getIssuer() {
        return issuer;
    }

    public RequiredClaims setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public String getName() {
        return name;
    }
}
