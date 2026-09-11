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
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * The Automation API representation of a data plane.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationDataPlane", title = "Data plane",
        description = "A data plane managed by the Automation API. Data planes store the runtime data of the " +
                "domains bound to them. The id field is the stable, immutable identity used for idempotent " +
                "create-or-update.")
public class AutomationDataPlane {

    @NotNull
    @Size(min = 1, max = 255)
    @Schema(description = "Stable, immutable identifier for the data plane within its environment. Lowercase " +
            "alphanumeric and hyphens, starting and ending with an alphanumeric character. This is the value " +
            "a domain's dataPlaneId refers to.",
            example = "acme-eu")
    private String id;

    @Size(min = 1, max = 255)
    @Schema(description = "Human-readable name of the data plane.", example = "ACME EU data plane")
    private String name;

    @Schema(description = "Data plane plugin type identifier, matching the dataplane-am-<type> plugin. " +
            "Immutable after creation.",
            example = "mongodb")
    private String type;

    @Schema(description = "Base URL of the gateway serving the domains bound to this data plane.",
            example = "https://gateway-eu.example.com")
    private String gatewayUrl;

    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, type = "object", implementation = Object.class,
            description = "Connection settings. Write-only: it can hold credentials.",
            example = "{\"mongodb\":{\"dbname\":\"gravitee-am-acme\",\"host\":\"mongo\",\"port\":27017}}")
    private JsonNode configuration;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Name of the database the configuration points at. Read-only.", example = "gravitee-am-acme")
    private String database;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Hosts the configuration points at, as host:port. Read-only.", example = "[\"mongo:27017\"]")
    private List<String> hosts;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Identifier of the organization the data plane belongs to. Read-only.", example = "DEFAULT")
    private String organizationId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Identifier of the environment the data plane belongs to. Read-only.", example = "DEFAULT")
    private String environmentId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Creation timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Last-update timestamp (ISO-8601 / RFC 3339, UTC). Read-only.")
    private Date updatedAt;
}
