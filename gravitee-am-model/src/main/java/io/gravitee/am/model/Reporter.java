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
public class Reporter {

    private String id;
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

    public Reporter() {
    }

    public Reporter(Reporter other) {
        this.id = other.id;
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
