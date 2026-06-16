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

/**
 * The Automation API representation of a reporter.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationReporter", title = "Reporter",
        description = "A reporter managed under a domain by the Automation API. Reporters persist audit " +
                "events to a backend. The key field is the stable, immutable identity used for idempotent " +
                "create-or-update.")
public class AutomationReporter {

    @NotNull
    @Size(min = 1, max = 255)
    @JsonProperty("key")
    @Schema(name = "key", title = "Key",
            description = "Stable, immutable identifier for the reporter within its domain. Lowercase " +
                    "alphanumeric and hyphens, starting and ending with an alphanumeric character. Used to " +
                    "identify the reporter on create-or-update.",
            example = "audit-kafka")
    private String automationKey;

    @Size(min = 1, max = 255)
    @Schema(description = "Human-readable name of the reporter.", example = "Audit events to Kafka")
    private String name;

    @Schema(description = "Reporter plugin type identifier. Immutable after creation.",
            example = "reporter-am-kafka")
    private String type;

    @Schema(description = "Plugin-specific configuration as a JSON-encoded string. Its shape is defined by " +
            "the selected reporter type.",
            example = "{\"bootstrapServers\":\"kafka:9092\",\"topic\":\"audit\"}")
    private String configuration;

    @Schema(description = "Whether the reporter is enabled.", defaultValue = "true")
    private boolean enabled = true;

    /**
     * Whether this is the domain's system reporter. Immutable: fixed at creation. When {@code true},
     * only {@code key} is required; the reporter is built from {@code domains.reporters.default.*}
     * settings (and the repository backend) in {@code gravitee.yaml} and the {@code name}, {@code type}
     * and {@code configuration} fields of this payload are ignored.
     */
    @JsonProperty("system")
    @Schema(name = "system",
            description = "Whether this is the domain's system reporter. Immutable after creation. When true, " +
                    "only key is required; the reporter is built from the domains.reporters.default.* and " +
                    "repository system settings and the name, type, and configuration fields are ignored.",
            defaultValue = "false")
    private boolean system;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Category of data the reporter handles, derived from its type. Read-only.")
    private String dataType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;
}
