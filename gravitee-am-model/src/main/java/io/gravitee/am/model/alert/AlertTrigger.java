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
import java.util.List;
import java.util.Objects;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertTrigger implements Resource {

    private String id;

    private AlertTriggerType type;

    private boolean enabled;

    private ReferenceType referenceType;

    private String referenceId;

    private List<String> alertNotifiers;

    private Date createdAt;

    private Date updatedAt;

    public AlertTrigger() {
    }

    public AlertTrigger(AlertTrigger other) {
        this.id = other.id;
        this.type = other.type;
        this.enabled = other.enabled;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.alertNotifiers = other.alertNotifiers;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AlertTriggerType getType() {
        return type;
    }

    public void setType(AlertTriggerType type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        AlertTrigger that = (AlertTrigger) o;
        return enabled == that.enabled && Objects.equals(id, that.id) && type == that.type && referenceType == that.referenceType && Objects.equals(referenceId, that.referenceId) && Objects.equals(alertNotifiers, that.alertNotifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, enabled, referenceType, referenceId, alertNotifiers);
    }

    public List<String> getAlertNotifiers() {
        return alertNotifiers;
    }

    public void setAlertNotifiers(List<String> alertNotifiers) {
        this.alertNotifiers = alertNotifiers;
    }
}