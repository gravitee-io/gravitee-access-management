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
package io.gravitee.am.repository.management.api.search;

import io.gravitee.am.model.application.ApplicationType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
public class ApplicationCriteria {

    /** null means "no status filter". */
    private Boolean enabled;

    /**
     * null means "no owner filter".
     * An empty list means the owner filter resolved to zero matches — callers
     * should short-circuit before reaching the repository, but implementations
     * must still return an empty page when this list is empty.
     */
    private List<String> applicationIds;

    /** null or empty means "no type filter"; otherwise the result is restricted to applications whose type is in this set. */
    private Set<ApplicationType> types;

    public Optional<Boolean> getEnabled() {
        return Optional.ofNullable(enabled);
    }

    public Optional<List<String>> getApplicationIds() {
        return Optional.ofNullable(applicationIds);
    }

    public Optional<Set<ApplicationType>> getTypes() {
        return (types == null || types.isEmpty()) ? Optional.empty() : Optional.of(types);
    }

    public ApplicationCriteria setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ApplicationCriteria setApplicationIds(List<String> applicationIds) {
        this.applicationIds = applicationIds;
        return this;
    }

    public ApplicationCriteria setTypes(Set<ApplicationType> types) {
        this.types = types;
        return this;
    }
}
