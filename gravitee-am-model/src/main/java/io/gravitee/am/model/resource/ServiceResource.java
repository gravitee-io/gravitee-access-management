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
package io.gravitee.am.model.resource;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Resource;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 *
 * A ServiceResource defines settings for a given media (ex: smtp configuration, SMS platform credentials...)
 * This is useful to avoid duplicate settings when multiple AM entities needs the same configuration.
 */
public class ServiceResource implements Resource {

    private String id;

    private ReferenceType referenceType;

    private String referenceId;

    private String name;

    private String type;

    private String configuration;

    private Date createdAt;

    private Date updatedAt;

    public ServiceResource() {
    }

    public ServiceResource(ServiceResource other) {
        this.id = other.id;
        this.referenceType = other.referenceType;
        this.referenceId = other.getReferenceId();
        this.name = other.name;
        this.type = other.getType();
        this.configuration = other.configuration;
        this.createdAt = new Date(other.getCreatedAt().getTime());
        this.updatedAt = new Date(other.getUpdatedAt().getTime());
    }



    @Override
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
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
}
