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

    private String policySetId;
    private int policySetVersion;
    /**
     * When true, the gateway always resolves the latest version of the policy set,
     * ignoring {@link #policySetVersion}.
     */
    private boolean policySetPinToLatest;

    private String schemaId;
    private int schemaVersion;
    /**
     * When true, the gateway always resolves the latest version of the schema,
     * ignoring {@link #schemaVersion}.
     */
    private boolean schemaPinToLatest;

    private String entityStoreId;
    private int entityStoreVersion;
    /**
     * When true, the gateway always resolves the latest version of the entity store,
     * ignoring {@link #entityStoreVersion}.
     */
    private boolean entityStorePinToLatest;

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
        this.policySetId = other.policySetId;
        this.policySetVersion = other.policySetVersion;
        this.policySetPinToLatest = other.policySetPinToLatest;
        this.schemaId = other.schemaId;
        this.schemaVersion = other.schemaVersion;
        this.schemaPinToLatest = other.schemaPinToLatest;
        this.entityStoreId = other.entityStoreId;
        this.entityStoreVersion = other.entityStoreVersion;
        this.entityStorePinToLatest = other.entityStorePinToLatest;
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
