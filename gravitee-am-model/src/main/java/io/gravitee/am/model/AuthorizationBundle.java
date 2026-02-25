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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.Objects;

/**
 * Atomic deployment unit for Cedar authorization: bundles together
 * policy text, entity data, and schema as a single versioned artefact.
 * One active bundle per domain.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthorizationBundle {

    private String id;

    private String domainId;

    private String name;

    private String description;

    /**
     * Engine type: "cedar" or "gapl".
     */
    private String engineType;

    /**
     * Cedar schema JSON defining entity types and actions.
     */
    private String schema;

    /**
     * Cedar policy text (one or more permit/forbid statements).
     */
    private String policies;

    /**
     * Cedar entities JSON array (entity definitions with attributes and hierarchy).
     */
    private String entities;

    /**
     * Auto-incremented on each update. Starts at 1.
     */
    private int version;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public AuthorizationBundle(AuthorizationBundle other) {
        this.id = other.id;
        this.domainId = other.domainId;
        this.name = other.name;
        this.description = other.description;
        this.engineType = other.engineType;
        this.schema = other.schema;
        this.policies = other.policies;
        this.entities = other.entities;
        this.version = other.version;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationBundle that = (AuthorizationBundle) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
