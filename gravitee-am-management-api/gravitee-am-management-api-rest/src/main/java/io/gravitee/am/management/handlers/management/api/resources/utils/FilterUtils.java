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
package io.gravitee.am.management.handlers.management.api.resources.utils;

import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FilterUtils {

    public static Audit filterAuditInfos(Audit audit) {
        Audit filteredAudit = new Audit();
        filteredAudit.setId(audit.getId());
        filteredAudit.setType(audit.getType());
        filteredAudit.setOutcome(filterAuditOutcome(audit.getOutcome()));
        filteredAudit.setTimestamp(audit.timestamp());
        filteredAudit.setActor(filterAuditEntityInfos(audit.getActor()));
        filteredAudit.setTarget(filterAuditEntityInfos(audit.getTarget()));

        return filteredAudit;
    }

    public static AuditOutcome filterAuditOutcome(AuditOutcome auditOutcome) {
        AuditOutcome filteredAuditOutcome = null;

        if (auditOutcome != null) {
            filteredAuditOutcome = new AuditOutcome();
            filteredAuditOutcome.setStatus(auditOutcome.getStatus());
        }

        return filteredAuditOutcome;
    }

    public static AuditEntity filterAuditEntityInfos(AuditEntity auditEntity) {
        AuditEntity filteredAuditEntity = null;

        if (auditEntity != null) {
            filteredAuditEntity = new AuditEntity();
            filteredAuditEntity.setId(auditEntity.getId());
            filteredAuditEntity.setType(auditEntity.getType());
            filteredAuditEntity.setAlternativeId(auditEntity.getAlternativeId());
            filteredAuditEntity.setDisplayName(auditEntity.getDisplayName());
            filteredAuditEntity.setReferenceType(auditEntity.getReferenceType());
            filteredAuditEntity.setReferenceId(auditEntity.getReferenceId());
        }

        return filteredAuditEntity;
    }
}
