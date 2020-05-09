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
package io.gravitee.am.gateway.handler.discovery.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Expose available Gravitee Access Management configuration endpoints.
 * See <a href="https://tools.ietf.org/html/rfc8615"></a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WellKnownMetadata {

    /**
     * See <a href="https://openid.net/specs/openid-connect-discovery-1_0.html"></a>
     */
    @JsonProperty("openid-configuration")
    private String openidConfiguration;

    /**
     * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#as-config"></a>
     */
    @JsonProperty("uma2-configuration")
    private String umaConfiguration;

    @JsonProperty("scim2-configuration")
    private String scim2Configuration;

    public String getOpenidConfiguration() {
        return openidConfiguration;
    }

    public WellKnownMetadata setOpenidConfiguration(String openidConfiguration) {
        this.openidConfiguration = openidConfiguration;
        return this;
    }

    public String getUmaConfiguration() {
        return umaConfiguration;
    }

    public WellKnownMetadata setUmaConfiguration(String umaConfiguration) {
        this.umaConfiguration = umaConfiguration;
        return this;
    }

    public String getScim2Configuration() {
        return scim2Configuration;
    }

    public WellKnownMetadata setScim2Configuration(String scim2Configuration) {
        this.scim2Configuration = scim2Configuration;
        return this;
    }
}
