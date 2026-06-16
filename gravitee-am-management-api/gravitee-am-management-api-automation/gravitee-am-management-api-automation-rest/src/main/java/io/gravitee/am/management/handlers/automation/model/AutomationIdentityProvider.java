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
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The Automation API representation of an Identity Provider.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationIdentityProvider", title = "Identity provider",
        description = "An identity provider managed under a domain by the Automation API. The key field is " +
                "the stable, immutable identity used for idempotent create-or-update.")
public class AutomationIdentityProvider {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key", title = "Key",
            description = "Stable, immutable identifier for the identity provider within its domain. Lowercase " +
                    "alphanumeric and hyphens, starting and ending with an alphanumeric character. Used to " +
                    "identify the identity provider on create-or-update.",
            example = "corporate-ldap")
    private String automationKey;

    @Size(min = 1, max = 255)
    @Schema(description = "Human-readable name of the identity provider.", example = "Corporate LDAP")
    private String name;

    @Schema(description = "Identity provider plugin type identifier. Immutable after creation.",
            example = "inline-am-idp")
    private String type;

    /**
     * Whether this is the domain's system identity provider. Immutable: fixed at creation. Drives the
     * conventional {@code default-idp-<domainId>} id so the gateway's registration fallback resolves to
     * it. When {@code true}, only {@code key} is required; the IDP is built from
     * {@code domains.identities.default.*} settings in {@code gravitee.yaml} and the {@code name},
     * {@code type} and {@code configuration} fields of this payload are ignored.
     */
    @JsonProperty("system")
    @Schema(name = "system",
            description = "Whether this is the domain's system identity provider. Immutable after creation. " +
                    "When true, only key is required; the identity provider is built from the " +
                    "domains.identities.default.* system settings and the name, type, and configuration fields " +
                    "are ignored.",
            defaultValue = "false")
    private boolean system;

    @Schema(description = "Plugin-specific configuration as a JSON-encoded string. Its shape is defined by " +
            "the selected identity provider type.",
            example = "{\"users\":[{\"username\":\"admin\",\"password\":\"...\"}]}")
    private String configuration;

    @Schema(description = "Attribute mappers: maps provider claims to AM user profile attributes.",
            example = "{\"sub\":\"username\",\"email\":\"email\"}")
    private Map<String, String> mappers;

    @Schema(description = "Role mapper: assigns AM roles based on provider attribute values. Each entry maps " +
            "a role to the user attribute expressions that grant it.")
    private Map<String, String[]> roleMapper;

    @Schema(description = "Group mapper: assigns AM groups based on provider attribute values. Each entry maps " +
            "a group to the user attribute expressions that grant it.")
    private Map<String, String[]> groupMapper;

    @Schema(description = "Email domains allowed to authenticate through this identity provider. When set, " +
            "users whose email domain is not listed are rejected.",
            example = "[\"example.com\"]")
    private List<String> domainWhitelist;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;
}
