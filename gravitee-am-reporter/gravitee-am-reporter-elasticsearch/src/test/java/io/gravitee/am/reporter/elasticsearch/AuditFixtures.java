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
package io.gravitee.am.reporter.elasticsearch;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @author GraviteeSource Team
 */
public final class AuditFixtures {

    private AuditFixtures() {
    }

    public static Audit audit(String referenceId, String type, Status status, Instant timestamp) {
        Audit audit = new Audit();
        audit.setId(UUID.randomUUID().toString());
        audit.setTransactionId(UUID.randomUUID().toString());
        audit.setReferenceType(ReferenceType.DOMAIN);
        audit.setReferenceId(referenceId);
        audit.setType(type);
        audit.setTimestamp(timestamp);

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(status);
        outcome.setMessage(status == Status.SUCCESS ? null : "something went wrong");
        audit.setOutcome(outcome);

        audit.setActor(entity("actor", referenceId));
        audit.setTarget(entity("target", referenceId));

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId("access-point-id");
        accessPoint.setAlternativeId("access-point-alternative-id");
        accessPoint.setDisplayName("gateway");
        accessPoint.setIpAddress("192.168.1.1");
        accessPoint.setUserAgent("Chrome");
        audit.setAccessPoint(accessPoint);

        return audit;
    }

    public static AuditEntity entity(String prefix, String referenceId) {
        AuditEntity entity = new AuditEntity();
        entity.setId(prefix + "-id");
        entity.setAlternativeId(prefix + "-alternative-id");
        entity.setType(prefix + "-type");
        entity.setDisplayName(prefix + " display name");
        entity.setReferenceType(ReferenceType.DOMAIN);
        entity.setReferenceId(referenceId);
        entity.setAttributes(Map.of("tenant", "acme"));
        return entity;
    }
}
