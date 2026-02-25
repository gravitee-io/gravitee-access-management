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
 * Immutable snapshot of an authorization bundle at a specific version.
 * Used for version history and rollback.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthorizationBundleVersion {

    private String id;

    private String bundleId;

    private String domainId;

    private int version;

    /**
     * Full snapshot of schema at this version.
     */
    private String schema;

    /**
     * Full snapshot of policies at this version.
     */
    private String policies;

    /**
     * Full snapshot of entities at this version.
     */
    private String entities;

    private String comment;

    private String createdBy;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationBundleVersion that = (AuthorizationBundleVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
