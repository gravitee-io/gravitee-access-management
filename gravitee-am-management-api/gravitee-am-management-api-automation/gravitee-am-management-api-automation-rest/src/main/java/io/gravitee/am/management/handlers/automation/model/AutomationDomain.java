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

import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.model.PasswordSettings;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The Automation API representation of a Domain.
 * <p>
 * Design:
 * <ul>
 *   <li>Unchanged domain settings reuse the shared {@code io.gravitee.am.model.*}
 *       sub-models <i>by reference</i> — no per-field mapping, full symmetry, and the
 *       shared schemas are never modified.</li>
 *   <li>Certificates, identity providers and reporters are <b>not</b> embedded — they are managed via
 *       their own {@code /domains/{domainKey}/...} endpoints. The domain only references some of them
 *       by <b>key</b>: {@link #saml} and {@link #certificateSettings} reference certificates,
 *       {@link #accountSettings} references an identity provider.</li>
 *   <li>{@code createdAt}/{@code updatedAt} are read-only; the writable key is
 *       {@code key}.</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AutomationDomain {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key")
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
            message = "key must be lowercase alphanumeric and hyphens, starting and ending with an alphanumeric character")
    private String automationKey;

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    private String description;

    private boolean enabled = true;

    @NotNull
    @Size(min = 1, max = 255)
    private String path;

    private Set<String> tags;

    private List<VirtualHost> vhosts;

    /**
     * Required and immutable after creation. The domain cannot be created without a
     * valid data plane, and it cannot be changed afterwards; it is part of the full
     * desired-state document but is never re-applied on update.
     */
    @NotNull
    @Size(min = 1, max = 255)
    private String dataPlaneId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, type = "java.lang.Long")
    private Date createdAt;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, type = "java.lang.Long")
    private Date updatedAt;

    // --- Shared settings reused by reference (full request/response symmetry) ---

    private UMASettings uma;
    private LoginSettings loginSettings;
    private WebAuthnSettings webAuthnSettings;
    private SCIMSettings scim;
    private PasswordSettings passwordSettings;
    private SelfServiceAccountManagementSettings selfServiceAccountManagementSettings;
    private CorsSettings corsSettings;
    private SecretExpirationSettings secretExpirationSettings;
    private TokenExchangeSettings tokenExchangeSettings;

    // --- Automation wrappers (key-keyed references) ---

    /**
     * OIDC settings. Wrapped (rather than reused by reference) because {@code cimdSettings}
     * is intentionally not surfaced — see {@link AutomationOidcSettings}.
     */
    @Valid
    private AutomationOidcSettings oidc;

    /**
     * Account settings. Wrapped because {@code defaultIdentityProviderForRegistration} is a
     * cross-resource reference that must be expressed in the key-only contract — see
     * {@link AutomationAccountSettings}.
     */
    @Valid
    private AutomationAccountSettings accountSettings;

    /**
     * Domain SAML 2.0 IdP settings. {@code saml.certificate} references a certificate of this domain by
     * its {@code key} (managed via the certificates endpoints).
     */
    @Valid
    private AutomationSamlSettings saml;

    /**
     * Domain certificate settings. {@code certificateSettings.fallbackCertificate} references a
     * certificate of this domain by its {@code key} (managed via the certificates endpoints).
     */
    @Valid
    private AutomationCertificateSettings certificateSettings;
}
