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
package io.gravitee.am.management.handlers.automation.model;

import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Automation API mirror of {@link io.gravitee.am.model.oidc.OIDCSettings}. All sub-blocks
 * that don't carry internal references are reused by reference from the shared model;
 * {@link #cibaSettings} is wrapped because it references authentication device notifiers
 * by name rather than id.
 * <p>
 * <b>Not surfaced:</b> {@code cimdSettings}. CIMD requires {@code templateId} (an internal
 * id of an {@link io.gravitee.am.model.Application}), and the Automation API does not yet
 * model Applications. Any existing CIMD configuration is reset on PUT.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationOidcSettings", title = "OpenID Connect settings",
        description = "OpenID Connect settings for the domain. CIMD (client identity metadata document) " +
                "settings are not exposed by the Automation API and are reset on update.")
public class AutomationOidcSettings {

    @Schema(description = "Dynamic Client Registration (DCR) settings for the domain.")
    @Valid
    private AutomationClientRegistrationSettings clientRegistrationSettings;

    @Schema(description = "Financial-grade API (FAPI) security profile settings for the domain.")
    private SecurityProfileSettings securityProfileSettings;

    @Schema(description = "Workload identity (SPIFFE) settings for the domain.")
    private SpiffeDomainSettings workloadIdentitySettings;

    @Schema(description = "Whether redirect_uri and post_logout_redirect_uri values are matched strictly " +
            "during OpenID Connect flows.", defaultValue = "false")
    private boolean redirectUriStrictMatching;

    @Schema(description = "URLs the user may be redirected to after sign-out (post_logout_redirect_uri).")
    private List<String> postLogoutRedirectUris;

    @Schema(description = "Allowed request_uri values for passing OpenID Connect request objects by reference.")
    private List<String> requestUris;

    @Schema(description = "Client-Initiated Backchannel Authentication (CIBA) settings for the domain.")
    @Valid
    private AutomationCIBASettings cibaSettings;
}
