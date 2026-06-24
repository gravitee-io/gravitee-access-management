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
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.model.PasswordSettings;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@Schema(name = "AutomationDomain", title = "Domain",
        description = "A security domain managed by the Automation API. The key field is the stable, " +
                "immutable identity used for idempotent create-or-update. Certificates, identity providers, " +
                "and reporters are not embedded; they are managed via the domain's sub-resource endpoints and " +
                "referenced here by key.")
public class AutomationDomain {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key", title = "Key",
            description = "Stable, immutable identifier for the domain within its environment. Lowercase " +
                    "alphanumeric and hyphens, starting and ending with an alphanumeric character. Used to " +
                    "identify the domain on create-or-update.",
            example = "example-domain")
    private String automationKey;

    @NotNull
    @Size(min = 1, max = 255)
    @Schema(description = "Human-readable name of the domain.", example = "Example domain")
    private String name;

    @Schema(description = "Human-readable description of the domain.", example = "An example authentication domain")
    private String description;

    @Schema(description = "Whether the domain handles incoming authentication and authorization requests.",
            defaultValue = "true")
    private boolean enabled = true;

    @Schema(description = "Whether this is the master domain of its environment. A master domain may perform " +
            "cross-domain token introspection.", defaultValue = "false")
    private boolean master;

    @Schema(description = "Whether alerting is enabled for the domain.")
    private Boolean alertEnabled;

    @NotNull
    @Size(min = 1, max = 255)
    @Schema(description = "Context path the domain is served under, relative to the gateway. Must start with a slash.",
            example = "/example-domain")
    private String path;

    @Schema(description = "Sharding tags that control which gateways deploy this domain.",
            example = "[\"eu\",\"production\"]")
    private Set<String> tags;

    @Schema(description = "Whether the domain is exposed through its virtual hosts rather than the default " +
            "context path. When true, vhosts must be supplied.", defaultValue = "false")
    private boolean vhostMode;

    @Schema(description = "Virtual hosts the domain is exposed on, overriding the default context path.")
    private List<VirtualHost> vhosts;

    /**
     * Required and immutable after creation. The domain cannot be created without a
     * valid data plane, and it cannot be changed afterwards; it is part of the full
     * desired-state document but is never re-applied on update.
     */
    @NotNull
    @Size(min = 1, max = 255)
    @Schema(description = "Identifier of the data plane this domain is connected to. Required at creation and " +
            "immutable afterwards; included in the desired-state document but never re-applied on update.",
            example = "default")
    private String dataPlaneId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;

    // --- Shared settings reused by reference (full request/response symmetry) ---

    private UMASettings uma;
    private LoginSettings loginSettings;
    private WebAuthnSettings webAuthnSettings;
    private SCIMSettings scim;
    private PasswordSettings passwordSettings;
    private SelfServiceAccountManagementSettings selfServiceAccountManagementSettings;
    private CorsSettings corsSettings;
    private WebProtectionSettings webProtectionSettings;
    private SecretExpirationSettings secretExpirationSettings;
    private TokenExchangeSettings tokenExchangeSettings;

    // --- Automation wrappers (key-keyed references) ---

    /**
     * OIDC settings. Wrapped (rather than reused by reference) because {@code cimdSettings}
     * is intentionally not surfaced — see {@link AutomationOidcSettings}.
     */
    @Schema(description = "OpenID Connect settings for the domain.")
    @Valid
    private AutomationOidcSettings oidc;

    /**
     * Account settings. Wrapped because {@code defaultIdentityProviderForRegistration} is a
     * cross-resource reference that must be expressed in the key-only contract — see
     * {@link AutomationAccountSettings}.
     */
    @Schema(description = "User account settings for the domain.")
    @Valid
    private AutomationAccountSettings accountSettings;

    /**
     * Domain SAML 2.0 IdP settings. {@code saml.certificate} references a certificate of this domain by
     * its {@code key} (managed via the certificates endpoints).
     */
    @Schema(description = "SAML 2.0 IdP settings for the domain.")
    @Valid
    private AutomationSamlSettings saml;

    /**
     * Domain certificate settings. {@code certificateSettings.fallbackCertificate} references a
     * certificate of this domain by its {@code key} (managed via the certificates endpoints).
     */
    @Schema(description = "Domain-level certificate settings.")
    @Valid
    private AutomationCertificateSettings certificateSettings;
}
