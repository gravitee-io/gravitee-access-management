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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.RoleScope;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleEntity {

    private String id;
    private String name;
    private String description;
    private String domain;
    private String scope;
    private List<String> permissions;
    private List<String> availablePermissions;
    private boolean system;
    private Date createdAt;
    private Date updatedAt;

    public RoleEntity(Role other) {
        this.id = other.getId();
        this.name = other.getName();
        this.description = other.getDescription();
        this.domain = other.getDomain();
        this.scope = convert(other.getScope());
        this.permissions = other.getPermissions();
        this.system = other.isSystem();
        this.createdAt = other.getCreatedAt();
        this.updatedAt = other.getUpdatedAt();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<String> getAvailablePermissions() {
        return availablePermissions;
    }

    public void setAvailablePermissions(List<String> availablePermissions) {
        this.availablePermissions = availablePermissions;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    private static String convert(Integer scope) {
        try {
            return scope != null ? RoleScope.valueOf(scope).name() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
