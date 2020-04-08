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

import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.permissions.SystemRole;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Role {

    private String id;
    private String name;
    private String description;
    private ReferenceType referenceType;
    private String referenceId;
    private ReferenceType assignableType;
    private boolean system;
    private boolean defaultRole;
    private Map<Permission, Set<Acl>> permissionAcls;
    private List<String> oauthScopes;
    private Date createdAt;
    private Date updatedAt;

    public Role() {
    }

    public Role(Role other) {
        this.id = other.id;
        this.name = other.name;
        this.description = other.description;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.assignableType = other.assignableType;
        this.permissionAcls = other.permissionAcls;
        this.oauthScopes = other.oauthScopes;
        this.system = other.system;
        this.defaultRole = other.defaultRole;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
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

    public boolean isSystem() {
        return system;
    }

    public boolean isInternalOnly() {

        return isSystem() && SystemRole.valueOf(name).isInternalOnly();
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(boolean defaultRole) {
        this.defaultRole = defaultRole;
    }

    public Map<Permission, Set<Acl>> getPermissionAcls() {
        return permissionAcls;
    }

    public void setPermissionAcls(Map<Permission, Set<Acl>> permissionAcls) {
        this.permissionAcls = permissionAcls;
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

    public List<String> getOauthScopes() {
        return oauthScopes;
    }

    public void setOauthScopes(List<String> oauthScopes) {
        this.oauthScopes = oauthScopes;
    }

    public ReferenceType getAssignableType() {
        return assignableType;
    }

    public void setAssignableType(ReferenceType assignableType) {
        this.assignableType = assignableType;
    }
}
