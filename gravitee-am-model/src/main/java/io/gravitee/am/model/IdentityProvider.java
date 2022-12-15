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

import io.swagger.annotations.ApiModelProperty;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProvider {

    private String id;

    private String name;

    private String type;

    private boolean system;

    private String configuration;

    private Map<String, String> mappers;

    private Map<String, String[]> roleMapper;

    private ReferenceType referenceType;

    private String referenceId;

    private boolean external;

    private List<String> domainWhitelist;

    @ApiModelProperty(dataType = "java.lang.Long")
    private Date createdAt;

    @ApiModelProperty(dataType = "java.lang.Long")
    private Date updatedAt;

    public IdentityProvider() {
    }

    public IdentityProvider(IdentityProvider other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.system = other.system;
        this.configuration = other.configuration;
        this.mappers = other.mappers;
        this.roleMapper = other.roleMapper;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.external = other.external;
        this.domainWhitelist = other.domainWhitelist;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public Map<String, String> getMappers() {
        return mappers;
    }

    public void setMappers(Map<String, String> mappers) {
        this.mappers = mappers;
    }

    public Map<String, String[]> getRoleMapper() {
        return roleMapper;
    }

    public void setRoleMapper(Map<String, String[]> roleMapper) {
        this.roleMapper = roleMapper;
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

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public List<String> getDomainWhitelist() {
        return domainWhitelist;
    }

    public void setDomainWhitelist(List<String> domainWhitelist) {
        this.domainWhitelist = domainWhitelist;
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

        IdentityProvider that = (IdentityProvider) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
