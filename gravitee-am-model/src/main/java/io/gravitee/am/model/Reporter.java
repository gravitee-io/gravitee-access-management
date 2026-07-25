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
package io.gravitee.am.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class Reporter implements Managed {

    private String id;

    /**
     * Stable, human-chosen identifier used to address this reporter in declarative APIs (e.g. the
     * Automation API). Set when the Automation API creates the resource; null for reporters created
     * via the management API. Read-only over the management API.
     */
    @JsonProperty("key")
    @Schema(name = "key", accessMode = Schema.AccessMode.READ_ONLY)
    private String automationKey;

    private Reference reference;
    private boolean enabled;
    private String type;
    private String name;
    private boolean system;
    private String dataType;
    private String configuration;
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;
    /**
     * If an organization level is inherited, it will automatically report events from all domains in this organization.
     * This has no effect on domain reporters.
     */
    private boolean inherited;

    /**
     * Indicates the source of truth for this reporter.
     */
    private ManagedBy managedBy;

    /**
     * True for the one reporter currently answering audit reads for this reference. Audit reads
     * resolve to a single reporter, and which one wins depends on state the Console cannot see —
     * whether a reporter's plugin can search at all, and whether it has finished starting. Resolved
     * per request against the running reporters, never persisted.
     */
    @Schema(name = "readSource", accessMode = Schema.AccessMode.READ_ONLY,
            description = "True for the reporter currently serving audit reads for this domain or organization.")
    private boolean readSource;

    /**
     * Whether the running instance of this reporter is usable. A reporter whose configuration the
     * store refuses is enabled, deployed and broken, and looks identical to a working one without
     * this. Resolved per request against the running reporters, never persisted.
     */
    @Schema(name = "status", accessMode = Schema.AccessMode.READ_ONLY,
            description = "Runtime state of this reporter: STARTING while it is still coming up, READY once it is " +
                    "answering, FAILED when it will not recover without a configuration change.")
    private ReporterStatus status;

    public Reporter() {
    }

    public Reporter(Reporter other) {
        this.id = other.id;
        this.automationKey = other.automationKey;
        this.reference = other.reference;
        this.enabled = other.enabled;
        this.type = other.type;
        this.name = other.name;
        this.system = other.system;
        this.dataType = other.dataType;
        this.configuration = other.configuration;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.inherited = other.inherited;
        this.managedBy = other.managedBy;
        this.readSource = other.readSource;
        this.status = other.status;
    }

    /**
     * @param filtered - if true, only the most basic set of information will be exposed
     * @return representation of this Reporter that can be exposed on the api.
     */
    public Reporter apiRepresentation(boolean filtered) {
        if (filtered) {
            return builder()
                    .id(getId())
                    .name(getName())
                    .type(getType())
                    .build();
        } else if (isSystem()) {
            return toBuilder().configuration(null).build();
        } else {
            return this;
        }
    }
}
