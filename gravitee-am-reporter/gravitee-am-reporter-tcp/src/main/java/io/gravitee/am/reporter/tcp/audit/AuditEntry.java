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
package io.gravitee.am.reporter.tcp.audit;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.tcp.formatter.ReportEntry;
import lombok.Data;

import java.time.Instant;

/**
 * Wire representation of an {@link io.gravitee.am.reporter.api.audit.model.Audit} event,
 * enriched with context information (organization, environment, node).
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class AuditEntry implements ReportEntry {

    private String id;
    private String transactionId;
    private String type;
    private ReferenceType referenceType;
    private String referenceId;
    private AuditAccessPoint accessPoint;
    private AuditEntity actor;
    private AuditEntity target;
    private AuditOutcome outcome;
    private Instant timestamp;
    private String environmentId;
    private String organizationId;
    private String nodeId;
    private String nodeHostname;
}
