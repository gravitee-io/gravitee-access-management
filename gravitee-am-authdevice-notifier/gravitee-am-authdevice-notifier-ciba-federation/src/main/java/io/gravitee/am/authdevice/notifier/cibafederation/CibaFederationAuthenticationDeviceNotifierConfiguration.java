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
package io.gravitee.am.authdevice.notifier.cibafederation;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierConfiguration;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CibaFederationAuthenticationDeviceNotifierConfiguration implements AuthenticationDeviceNotifierConfiguration {
    // Downstream resource/audience indicator for the resource server hosting the consent
    // authorization_details schema. Sent as the `audience` form parameter on bc-authorize whenever it
    // is configured (non-blank) — this is the provider's resource-indicator parameter
    // name, NOT RFC 8707 `resource`. It is not an IdP-config property (the OIDC IdP schema cannot carry
    // it), so it lives here. clientId/secret/scope/wellKnownUri come from the gateway-resolved IdP bundle.
    private String resourceAudience;
    // The Gravitee AM identity provider (IdP) that this notifier federates to. Used by the gateway to
    // resolve the OIDC connection bundle (clientId/secret/wellKnownUri/scope) per request.
    private String identityProviderId;
    private String callbackClientId;
    private String callbackClientSecret;
    private String recipientDisplayName = "the requesting application";
    private String consentRelayStrategy = "passthrough";
    private Integer maxLifetimeSeconds = 120;
}
