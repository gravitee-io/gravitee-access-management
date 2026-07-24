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
package io.gravitee.am.reporter.elasticsearch.audit;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditAccessPointDocument;
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditDocument;
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditEntityDocument;
import io.gravitee.am.reporter.elasticsearch.audit.model.AuditOutcomeDocument;

/**
 * Converts between the AM {@link Audit} model and the Elasticsearch {@link AuditDocument} representation.
 *
 * @author GraviteeSource Team
 */
public final class AuditConverter {

    private AuditConverter() {
    }

    public static AuditDocument toDocument(Audit audit) {
        AuditDocument document = new AuditDocument();
        document.setId(audit.getId());
        document.setTransactionId(audit.getTransactionId());
        document.setReferenceType(audit.getReferenceType() != null ? audit.getReferenceType().name() : null);
        document.setReferenceId(audit.getReferenceId());
        document.setType(audit.getType());
        document.setTimestamp(audit.timestamp() != null ? audit.timestamp().toEpochMilli() : 0L);

        document.setActor(toEntityDocument(audit.getActor()));
        document.setTarget(toEntityDocument(audit.getTarget()));

        if (audit.getAccessPoint() != null) {
            AuditAccessPoint accessPoint = audit.getAccessPoint();
            AuditAccessPointDocument accessPointDocument = new AuditAccessPointDocument();
            accessPointDocument.setId(accessPoint.getId());
            accessPointDocument.setAlternativeId(accessPoint.getAlternativeId());
            accessPointDocument.setDisplayName(accessPoint.getDisplayName());
            accessPointDocument.setIpAddress(accessPoint.getIpAddress());
            accessPointDocument.setUserAgent(accessPoint.getUserAgent());
            document.setAccessPoint(accessPointDocument);
        }

        if (audit.getOutcome() != null) {
            AuditOutcome outcome = audit.getOutcome();
            AuditOutcomeDocument outcomeDocument = new AuditOutcomeDocument();
            outcomeDocument.setStatus(outcome.getStatus() != null ? outcome.getStatus().name() : null);
            outcomeDocument.setMessage(outcome.getMessage());
            document.setOutcome(outcomeDocument);
        }

        return document;
    }

    public static Audit toAudit(AuditDocument document) {
        Audit audit = new Audit();
        audit.setId(document.getId());
        audit.setTransactionId(document.getTransactionId());
        audit.setReferenceType(document.getReferenceType() != null ? ReferenceType.valueOf(document.getReferenceType()) : null);
        audit.setReferenceId(document.getReferenceId());
        audit.setType(document.getType());
        audit.setTimestamp(java.time.Instant.ofEpochMilli(document.getTimestamp()));

        audit.setActor(toEntity(document.getActor()));
        audit.setTarget(toEntity(document.getTarget()));

        if (document.getAccessPoint() != null) {
            AuditAccessPointDocument accessPointDocument = document.getAccessPoint();
            AuditAccessPoint accessPoint = new AuditAccessPoint();
            accessPoint.setId(accessPointDocument.getId());
            accessPoint.setAlternativeId(accessPointDocument.getAlternativeId());
            accessPoint.setDisplayName(accessPointDocument.getDisplayName());
            accessPoint.setIpAddress(accessPointDocument.getIpAddress());
            accessPoint.setUserAgent(accessPointDocument.getUserAgent());
            audit.setAccessPoint(accessPoint);
        }

        if (document.getOutcome() != null) {
            AuditOutcomeDocument outcomeDocument = document.getOutcome();
            AuditOutcome outcome = new AuditOutcome();
            outcome.setStatus(outcomeDocument.getStatus() != null ? Status.valueOf(outcomeDocument.getStatus()) : null);
            outcome.setMessage(outcomeDocument.getMessage());
            audit.setOutcome(outcome);
        }

        return audit;
    }

    private static AuditEntityDocument toEntityDocument(AuditEntity entity) {
        if (entity == null) {
            return null;
        }
        AuditEntityDocument document = new AuditEntityDocument();
        document.setId(entity.getId());
        document.setAlternativeId(entity.getAlternativeId());
        document.setType(entity.getType());
        document.setDisplayName(entity.getDisplayName());
        document.setReferenceType(entity.getReferenceType() != null ? entity.getReferenceType().name() : null);
        document.setReferenceId(entity.getReferenceId());
        document.setAttributes(entity.getAttributes());
        return document;
    }

    private static AuditEntity toEntity(AuditEntityDocument document) {
        if (document == null) {
            return null;
        }
        AuditEntity entity = new AuditEntity();
        entity.setId(document.getId());
        entity.setAlternativeId(document.getAlternativeId());
        entity.setType(document.getType());
        entity.setDisplayName(document.getDisplayName());
        entity.setReferenceType(document.getReferenceType() != null ? ReferenceType.valueOf(document.getReferenceType()) : null);
        entity.setReferenceId(document.getReferenceId());
        entity.setAttributes(document.getAttributes());
        return entity;
    }
}
