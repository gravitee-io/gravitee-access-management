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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Composition pointer that ties together a {@link PolicySet}, an {@link AuthorizationSchema},
 * and an {@link EntityStore} at specific versions. Multiple bundles per domain are allowed;
 * each authorization engine (sidecar) references one bundle via its configuration.
 * <p>
 * The bundle itself is not versioned — version history lives in each component.
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

    // --- Component references ---

    /**
     * Ordered list of policy set references. Order is preserved from the UI.
     */
    private List<BundleComponentRef> policySets = new ArrayList<>();

    /**
     * Ordered list of entity store references. Order is preserved from the UI.
     */
    private List<BundleComponentRef> entityStores = new ArrayList<>();

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
        this.policySets = other.policySets != null ? new ArrayList<>(other.policySets) : new ArrayList<>();
        this.entityStores = other.entityStores != null ? new ArrayList<>(other.entityStores) : new ArrayList<>();
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
