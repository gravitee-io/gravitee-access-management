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
package io.gravitee.am.repository.jdbc.management.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("alert_triggers")
public class JdbcAlertTrigger {

    @Id
    private String id;

    @Column("enabled")
    private boolean enabled;

    @Column("reference_type")
    private String referenceType;

    @Column("reference_id")
    private String referenceId;

    @Column("type")
    private String type;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Bean class to provide list of alert notifiers linked to the AlertTrigger.
     */
    @Table("alert_triggers_alert_notifiers")
    public static class AlertNotifier {

        @Column("alert_trigger_id")
        String alertTriggerId;

        @Column("alert_notifier_id")
        String alertNotifierId;

        public String getAlertTriggerId() {
            return alertTriggerId;
        }

        public void setAlertTriggerId(String alertTriggerId) {
            this.alertTriggerId = alertTriggerId;
        }

        public String getAlertNotifierId() {
            return alertNotifierId;
        }

        public void setAlertNotifierId(String alertNotifierId) {
            this.alertNotifierId = alertNotifierId;
        }
    }
}