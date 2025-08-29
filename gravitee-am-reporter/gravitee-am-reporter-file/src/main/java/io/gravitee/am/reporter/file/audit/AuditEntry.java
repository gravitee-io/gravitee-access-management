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
package io.gravitee.am.reporter.file.audit;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class AuditEntry implements ReportEntry {

    /**
     * Identifier for a specific audited event
     */
    private String id;

    /**
     * The log transaction id for event correlation
     */
    private String transactionId;

    /**
     * Indicator for type of action performed during the event that generated the audit
     */
    private String type;

    /**
     * The type of the resource who triggered the action
     */
    private ReferenceType referenceType;

    /**
     * The id of the resource who triggered the action
     */
    private String referenceId;

    /**
     * The access point that performs the event (OAuth client or HTTP client (e.g web browser) with ip address, user agent, geographical information)
     */
    private AuditAccessPoint accessPoint;

    /**
     * Describes the user, app, client, or other entity who performed an action on a target
     */
    private AuditEntity actor;

    /**
     * The entity upon which an actor performs an action
     */
    private AuditEntity target;

    /**
     * The result of the action
     */
    private AuditOutcome outcome;

    /**
     * The date when the event was logged
     */
    private Instant timestamp;

    /**
     * The environment identifier
     */
    private String environmentId;

    /**
     * The organization identifier
     */
    private String organizationId;

    private String nodeId;

    private String nodeHostname;

    /**
     * Indicates whether the event succeeded or failed
     * @deprecated since 4.5, status is a part of {@link #outcome}
     */
    @Deprecated(since = "4.5", forRemoval = true)
    public String getStatus() {
        return outcome == null || outcome.getStatus() == null ? null : outcome.getStatus().name();
    }

}
