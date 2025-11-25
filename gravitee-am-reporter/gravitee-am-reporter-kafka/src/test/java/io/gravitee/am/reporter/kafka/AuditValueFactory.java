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
package io.gravitee.am.reporter.kafka;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;

import java.util.HashMap;

import lombok.experimental.UtilityClass;

import java.time.Instant;

@UtilityClass
public class AuditValueFactory {

    public static Audit createAudit() {
        Audit audit = new Audit();
        audit.setId("id");
        audit.setReferenceId("reference id");
        audit.setReferenceType(ReferenceType.APPLICATION);
        audit.setTimestamp(Instant.EPOCH);
        audit.setTransactionId("transaction id");
        audit.setType("type");

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        audit.setOutcome(outcome);

        AuditEntity actor = createAuditEntity("actor");
        audit.setActor(actor);
        AuditEntity target = createAuditEntity("target");
        audit.setTarget(target);

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId("access point id");
        accessPoint.setAlternativeId("access point alternative id");
        accessPoint.setDisplayName("access Display Name");
        accessPoint.setUserAgent("Chrome");
        accessPoint.setIpAddress("192.168.1.1");
        audit.setAccessPoint(accessPoint);

        return audit;
    }

    public static Audit createAuditOfType(String type) {
        var audit = createAudit();
        audit.setType(type);
        return audit;
    }

    public static AuditEntity createAuditEntity(final String prefix) {
        AuditEntity actor = new AuditEntity();
        actor.setId(prefix + " id");
        actor.setType(prefix + " type");
        actor.setDisplayName(prefix + " display name");
        actor.setAlternativeId(prefix + " alternative id");
        actor.setReferenceId(prefix + " reference id");
        actor.setReferenceType(ReferenceType.APPLICATION);
        actor.setAttributes(new HashMap<>());
        return actor;
    }
}
