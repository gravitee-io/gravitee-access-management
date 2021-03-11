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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertTriggerAuditBuilder extends ManagementAuditBuilder<AlertTriggerAuditBuilder> {

    public AlertTriggerAuditBuilder() {
        super();
    }

    public AlertTriggerAuditBuilder alertTrigger(AlertTrigger alertTrigger) {
    if (EventType.ALERT_TRIGGER_CREATED.equals(getType()) || EventType.ALERT_TRIGGER_UPDATED.equals(getType())) {
            setNewValue(alertTrigger);
        }

        referenceType(alertTrigger.getReferenceType());
        referenceId(alertTrigger.getReferenceId());

        setTarget(alertTrigger.getId(), EntityType.ALERT_TRIGGER, null, alertTrigger.getType().name(), alertTrigger.getReferenceType(), alertTrigger.getReferenceId());
        return this;
    }
}
