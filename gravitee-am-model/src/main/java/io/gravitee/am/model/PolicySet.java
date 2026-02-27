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
 * Metadata for a policy set containing authorization policies (e.g., Cedar permit/forbid statements).
 * Reusable across multiple {@link AuthorizationBundle}s within the same domain.
 * <p>
 * Content is stored in {@link PolicySetVersion} records — each update creates a new version.
 */
@Getter
@Setter
@NoArgsConstructor
public class PolicySet {

    private String id;

    private String domainId;

    private String name;

    /**
     * Denormalised latest version number. Matches the highest version in {@link PolicySetVersion}.
     */
    private int latestVersion;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicySet that = (PolicySet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
