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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.application.ApplicationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The Automation API representation of an application.
 * <p>
 * It carries what the Management API lets an application change, plus {@code type} and {@code clientId}, minus
 * the client secrets and the resources managed elsewhere (roles, groups, flows, forms, email templates, themes,
 * members). Certificate and identity-provider references are eventually consistent.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationApplication", title = "Application",
        description = "An application (OAuth 2.0 / OIDC client) managed under a domain by the Automation API. The key " +
                "field is the stable, immutable identity used for idempotent create-or-update.")
public class AutomationApplication implements DryRunResult {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key", title = "Key",
            description = "Stable, immutable identifier for the application within its domain. Lowercase " +
                    "alphanumeric and hyphens, starting and ending with an alphanumeric character. Used to " +
                    "identify the application on create-or-update.",
            example = "mobile-app")
    private String automationKey;

    @NotNull
    @Size(min = 1, max = 255)
    @Schema(description = "Human-readable name of the application. Also used as the OAuth 2.0 client name.",
            example = "Mobile app")
    private String name;

    @Schema(description = "Description of the application.", example = "Customer mobile application")
    private String description;

    @NotNull
    @Schema(description = "Application type. It drives the computed OAuth 2.0 settings. Can be changed, except to " +
            "or from agent: such a change is rejected with 400 (delete and recreate the application). Changing the " +
            "type resets grantTypes, responseTypes and tokenEndpointAuthMethod to the new type's defaults before " +
            "the payload applies.", example = "native")
    private ApplicationType type;

    @Schema(description = "Agent persona. Required when type is agent, rejected otherwise.",
            allowableValues = {"USER_EMBEDDED", "HOSTED_DELEGATED", "AUTONOMOUS"})
    private String kind;

    @Schema(description = "Whether the application is enabled. Disabling it revokes its tokens.", defaultValue = "true")
    private boolean enabled = true;

    @Schema(description = "Whether the application is a registration template for dynamic client registration " +
            "rather than an active application.", defaultValue = "false")
    private boolean template;

    @Schema(description = "OAuth 2.0 client identifier. Generated when omitted on creation. Immutable once set: " +
            "a different value on update is rejected with 400, an omitted one keeps the current value. Must be " +
            "unique within the domain.", example = "mobile-app-client")
    private String clientId;

    @NotNull
    @Schema(description = "Certificate signing the tokens: the key of a certificate of the domain, or id:<uuid>. " +
            "The reference is not checked against existing certificates: it can name one created after the " +
            "application or since deleted, and resolves whenever that certificate exists.",
            example = "signing-cert")
    private String certificate;

    @Schema(description = "Free-form metadata of the application.", example = "{\"team\": \"mobile\"}")
    private Map<String, Object> metadata;

    @Valid
    @Schema(description = "Identity providers users can authenticate with on this application.")
    private List<AutomationApplicationIdentityProvider> identityProviders;

    @Valid
    @Schema(description = "Settings of the application.")
    private AutomationApplicationSettings settings;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Validation errors returned when dryRun is true. Absent when validation succeeds.")
    @Setter(lombok.AccessLevel.NONE)
    private List<DryRunError> dryRunErrors;

    public void setDryRunErrors(List<DryRunError> dryRunErrors) {
        this.dryRunErrors = dryRunErrors != null && !dryRunErrors.isEmpty() ? dryRunErrors : null;
    }
}
