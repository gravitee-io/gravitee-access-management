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
package io.gravitee.am.model.alert;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Resource;

import java.util.Date;
import java.util.Objects;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertNotifier implements Resource {

    private String id;

    private String name;

    private String type;

    private boolean enabled;

    private String configuration;

    private ReferenceType referenceType;

    private String referenceId;

    private Date createdAt;

    private Date updatedAt;

    public AlertNotifier() {
    }

    public AlertNotifier(AlertNotifier other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.enabled = other.enabled;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.configuration = other.getConfiguration();
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertNotifier that = (AlertNotifier) o;
        return enabled == that.enabled && Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(type, that.type)
                && Objects.equals(configuration, that.configuration) && referenceType == that.referenceType && Objects.equals(referenceId, that.referenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, enabled, configuration, referenceType, referenceId);
    }
}
