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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.Acl;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import java.util.*;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleMongo extends Auditable {

    @BsonId
    private String id;

    private String name;

    private String description;

    private String referenceType;

    private String referenceId;

    private String assignableType;

    private boolean system;

    private boolean defaultRole;

    private Map<String, Set<Acl>> permissionAcls;

    private List<String> oauthScopes;

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

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
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

    public void setSystem(boolean system) {
        this.system = system;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RoleMongo roleMongo = (RoleMongo) o;

        return id != null ? id.equals(roleMongo.id) : roleMongo.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public String getAssignableType() {
        return assignableType;
    }

    public void setAssignableType(String assignableType) {
        this.assignableType = assignableType;
    }

    public Map<String, Set<Acl>> getPermissionAcls() {
        return permissionAcls;
    }

    public void setPermissionAcls(Map<String, Set<Acl>> permissionAcls) {
        this.permissionAcls = permissionAcls;
    }

    public List<String> getOauthScopes() {
        return oauthScopes;
    }

    public void setOauthScopes(List<String> oauthScopes) {
        this.oauthScopes = oauthScopes;
    }

    public boolean isDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(boolean defaultRole) {
        this.defaultRole = defaultRole;
    }
}
