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

import java.util.List;

/**
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#IndividualClaimsRequests">5.5.1. Individual Claims Requests</a>
 *
 * Claim being requested.
 *
 * The member values MUST be one of the following:
 *
 * null
 *  Indicates that this Claim is being requested in the default manner. In particular, this is a Voluntary Claim. For instance, the Claim request:
 *   "given_name": null
 *  requests the given_name Claim in the default manner.
 *
 * JSON Object
 *
 * Used to provide additional information about the Claim being requested. This specification defines the following members:
 *
 * essential
 * OPTIONAL. Indicates whether the Claim being requested is an Essential Claim. If the value is true, this indicates that the Claim is an Essential Claim. For instance, the Claim request:
 *   "auth_time": {"essential": true}
 * can be used to specify that it is Essential to return an auth_time Claim Value.
 * If the value is false, it indicates that it is a Voluntary Claim. The default is false.
 * By requesting Claims as Essential Claims, the RP indicates to the End-User that releasing these Claims will ensure a smooth authorization for the specific task requested by the End-User. Note that even if the Claims are not available because the End-User did not authorize their release or they are not present, the Authorization Server MUST NOT generate an error when Claims are not returned, whether they are Essential or Voluntary, unless otherwise specified in the description of the specific claim.
 *
 * value
 * OPTIONAL. Requests that the Claim be returned with a particular value. For instance the Claim request:
 *   "sub": {"value": "248289761001"}
 * can be used to specify that the request apply to the End-User with Subject Identifier 248289761001.
 * The value of the value member MUST be a valid value for the Claim being requested. Definitions of individual Claims can include requirements on how and whether the value qualifier is to be used when requesting that Claim.
 *
 * values
 * OPTIONAL. Requests that the Claim be returned with one of a set of values, with the values appearing in order of preference. For instance the Claim request:
 *   "acr": {"essential": true,
 *           "values": ["urn:mace:incommon:iap:silver",
 *                      "urn:mace:incommon:iap:bronze"]}
 * specifies that it is Essential that the acr Claim be returned with either the value urn:mace:incommon:iap:silver or urn:mace:incommon:iap:bronze.
 * The values in the values member array MUST be valid values for the Claim being requested. Definitions of individual Claims can include requirements on how and whether the values qualifier is to be used when requesting that Claim.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimRequest {

    /**
     * OPTIONAL. Indicates whether the Claim being requested is an Essential Claim.
     * If the value is true, this indicates that the Claim is an Essential Claim.
     */
    private Boolean essential;
    /**
     * OPTIONAL. Requests that the Claim be returned with a particular value
     */
    private String value;
    /**
     * OPTIONAL. Requests that the Claim be returned with one of a set of values, with the values appearing in order of preference.
     */
    private List<String> values;

    public Boolean getEssential() {
        return essential;
    }

    public void setEssential(Boolean essential) {
        this.essential = essential;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
