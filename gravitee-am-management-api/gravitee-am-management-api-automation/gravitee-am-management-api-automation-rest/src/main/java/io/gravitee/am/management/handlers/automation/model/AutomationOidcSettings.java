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

import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.SecurityProfileSettings;
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
public class AutomationOidcSettings {

    private ClientRegistrationSettings clientRegistrationSettings;

    private SecurityProfileSettings securityProfileSettings;

    /** Enable redirect_uri strict matching during OIDC flow. */
    private boolean redirectUriStrictMatching;

    private List<String> postLogoutRedirectUris;

    private List<String> requestUris;

    /** CIBA settings. */
    @Valid
    private AutomationCIBASettings cibaSettings;
}
