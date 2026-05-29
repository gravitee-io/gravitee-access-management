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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class AutomationReporter {

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

    private String configuration;

    private boolean enabled = true;

    /**
     * Whether this is the domain's system reporter. Immutable: fixed at creation. When {@code true},
     * only {@code key} is required; the reporter is built from {@code domains.reporters.default.*}
     * settings (and the repository backend) in {@code gravitee.yaml} and the {@code name}, {@code type}
     * and {@code configuration} fields of this payload are ignored.
     */
    @JsonProperty("system")
    @Schema(name = "system", description = "whether this is the domain's system reporter (immutable after creation; when true, only key is required and the reporter is built from domains.reporters.default.* and repository system settings)")
    private boolean system;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String dataType;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, type = "java.lang.Long")
    private Date createdAt;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, type = "java.lang.Long")
    private Date updatedAt;
}
