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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleEntity {

    private String id;
    private String name;
    private String description;
    private ReferenceType referenceType;
    private String referenceId;
    private String assignableType;
    private List<String> permissions;
    private List<String> availablePermissions;
    private boolean system;
    private Date createdAt;
    private Date updatedAt;

    public RoleEntity() {
        super();
    }

    public RoleEntity(Role other) {
        this.id = other.getId();
        this.name = other.getName();
        this.description = other.getDescription();
        this.referenceType = other.getReferenceType();
        this.referenceId = other.getReferenceId();
        this.assignableType = other.getAssignableType() == null ? null : other.getAssignableType().name();
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

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
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

    public String getAssignableType() {
        return assignableType;
    }

    public void setAssignableType(String assignableType) {
        this.assignableType = assignableType;
    }
}
