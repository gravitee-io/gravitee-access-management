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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Domain SAML 2.0 IdP settings as exposed by the Automation API.
 * <p>
 * {@code certificate} references — by {@code key} — a certificate managed under the same
 * domain. The reference is eventually consistent: that certificate may not exist yet, or
 * any more.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationSamlSettings", title = "SAML 2.0 settings",
        description = "Settings for the domain acting as a SAML 2.0 identity provider (IdP).")
public class AutomationSamlSettings {

    @Schema(description = "Whether the domain exposes the SAML 2.0 IdP protocol.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "URL or URN that uniquely identifies this IdP (the SAML entity ID).",
            example = "https://auth.example.com/saml2/idp/entity")
    private String entityId;

    @Schema(description = "Key of a certificate managed under this domain, used to sign SAML responses. " +
            "The reference is not checked against existing certificates: it can name one created after the " +
            "domain or since deleted, and resolves whenever a certificate with that key exists.",
            example = "signing-cert")
    private String certificate;
}
