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
import jakarta.validation.constraints.Pattern;
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
public class AutomationIdentityProvider {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key")
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
            message = "key must be lowercase alphanumeric and hyphens, starting and ending with an alphanumeric character")
    private String automationKey;

    @Size(min = 1, max = 255)
    private String name;

    private String type;

    /**
     * Whether this is the domain's system identity provider. Immutable: fixed at creation. Drives the
     * conventional {@code default-idp-<domainId>} id so the gateway's registration fallback resolves to
     * it. When {@code true}, only {@code key} is required; the IDP is built from
     * {@code domains.identities.default.*} settings in {@code gravitee.yaml} and the {@code name},
     * {@code type} and {@code configuration} fields of this payload are ignored.
     */
    @JsonProperty("system")
    @Schema(name = "system", description = "whether this is the domain's system identity provider (immutable after creation; when true, only key is required and the IDP is built from domains.identities.default.* system settings)")
    private boolean system;

    private String configuration;

    private Map<String, String> mappers;

    private Map<String, String[]> roleMapper;

    private Map<String, String[]> groupMapper;

    private List<String> domainWhitelist;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;
}
