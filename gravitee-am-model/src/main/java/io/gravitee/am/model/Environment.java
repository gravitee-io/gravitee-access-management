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

import java.util.Date;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Environment implements Resource{

    public static final String DEFAULT = "DEFAULT";

    private String id;

    private List<String> hrids;

    private String name;

    private String description;

    private List<String> domainRestrictions;

    private String organizationId;

    private Date createdAt;

    private Date updatedAt;

    public Environment() {
        super();
    }

    public Environment(Environment cloned) {
        super();
        this.id = cloned.id;
        this.name = cloned.name;
        this.description = cloned.description;
        this.domainRestrictions = cloned.domainRestrictions;
        this.hrids = cloned.hrids;
        this.organizationId = cloned.organizationId;
        this.createdAt = cloned.createdAt;
        this.updatedAt = cloned.updatedAt;
    }

    @Override
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

    public List<String> getDomainRestrictions() {
        return domainRestrictions;
    }

    public void setDomainRestrictions(List<String> domainRestrictions) {
        this.domainRestrictions = domainRestrictions;
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

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public List<String> getHrids() {
        return hrids;
    }

    public void setHrids(List<String> hrids) {
        this.hrids = hrids;
    }
}
