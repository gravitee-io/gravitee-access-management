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
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthorizationEngine implements PluginConfigurableEntity<AuthorizationEngine> {

    private String id;

    private String name;

    private String type;

    private String configuration;

    private ReferenceType referenceType;

    private String referenceId;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public AuthorizationEngine(AuthorizationEngine other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.configuration = other.configuration;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    @Override
    public AuthorizationEngine copy() {
        return new AuthorizationEngine(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthorizationEngine that = (AuthorizationEngine) o;

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
