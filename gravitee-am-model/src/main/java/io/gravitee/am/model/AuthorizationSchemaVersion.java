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
 * Immutable snapshot of an {@link AuthorizationSchema} at a specific version.
 * Each update to an AuthorizationSchema creates a new AuthorizationSchemaVersion record;
 * previous versions are preserved for history, rollback, and diff.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthorizationSchemaVersion {

    private String id;

    /**
     * Reference to the parent {@link AuthorizationSchema}.
     */
    private String schemaId;

    /**
     * Sequential version number (1, 2, 3, ...). Unique within a given schemaId.
     */
    private int version;

    /**
     * Schema content at this version (e.g., Cedar schema JSON).
     */
    private String content;

    /**
     * Mandatory description of what changed in this version.
     */
    private String commitMessage;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    /**
     * User who created this version (principal ID from the authenticated user).
     */
    private String createdBy;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationSchemaVersion that = (AuthorizationSchemaVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
