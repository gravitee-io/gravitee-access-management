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
package io.gravitee.am.gateway.handler.uma.service.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.gateway.handler.oauth2.service.discovery.Oauth2ProviderMetadata;

import java.util.List;

/**
 * <pre>
 * User-Managed Access (UMA) 2.0 contains these two specifications :
 *  - UMA 2.0 Grant for OAuth 2.0 Authorization
 *  - Federated Authorization for UMA 2.0
 *
 * While the first one explain how to grant access, the second defines a means for AS & RS to be loosely coupled.
 * Both require additional metadata attributes extending the original oauth2 provider configuration metadata.
 *
 * UMA Grant requires : See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#as-config">here</a>
 *  - claims_interaction_endpoint
 *  - uma_profiles_supported
 *
 * Federated requires : see <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#as-config">here</a>
 * <i>They are part of the UMA2 AS <strong>Protection API</strong></i>
 * - permission_endpoint
 * - resource_registration_endpoint
 * - introspection_endpoint (already available in the oauth2 spec)
 *
 * See Original Oauth2 metadata <a href="https://tools.ietf.org/html/rfc8414#section-2">here</a>
 * </pre>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UMAProviderMetadata extends Oauth2ProviderMetadata {

    @JsonProperty("claims_interaction_endpoint")
    private String claimsInteractionEndpoint;

    @JsonProperty("uma_profiles_supported")
    private List<String> umaProfilesSupported;

    public String getClaimsInteractionEndpoint() {
        return claimsInteractionEndpoint;
    }

    @JsonProperty("permission_endpoint")
    private String permissionEndpoint;

    @JsonProperty("resource_registration_endpoint")
    private String resourceRegistrationEndpoint;

    public UMAProviderMetadata setClaimsInteractionEndpoint(String claimsInteractionEndpoint) {
        this.claimsInteractionEndpoint = claimsInteractionEndpoint;
        return this;
    }

    public List<String> getUmaProfilesSupported() {
        return umaProfilesSupported;
    }

    public UMAProviderMetadata setUmaProfilesSupported(List<String> umaProfilesSupported) {
        this.umaProfilesSupported = umaProfilesSupported;
        return this;
    }

    public String getPermissionEndpoint() {
        return permissionEndpoint;
    }

    public UMAProviderMetadata setPermissionEndpoint(String permissionEndpoint) {
        this.permissionEndpoint = permissionEndpoint;
        return this;
    }

    public String getResourceRegistrationEndpoint() {
        return resourceRegistrationEndpoint;
    }

    public UMAProviderMetadata setResourceRegistrationEndpoint(String resourceRegistrationEndpoint) {
        this.resourceRegistrationEndpoint = resourceRegistrationEndpoint;
        return this;
    }
}
