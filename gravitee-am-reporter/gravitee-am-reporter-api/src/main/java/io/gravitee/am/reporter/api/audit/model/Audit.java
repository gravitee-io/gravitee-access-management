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
package io.gravitee.am.reporter.api.audit.model;

import io.gravitee.am.reporter.api.Reportable;

import java.time.Instant;

/**
 * Security Audit based on RFC 3881 - Security Audit and Access Accountability Message
 *
 * See <a href="https://tools.ietf.org/html/rfc3881#section-5">5. Data Definitions</a>
 *
 * 1) Event Identification - what was done
 * 2) Active Participant Identification - by whom
 * 3) Network Access Point Identification - initiated from where
 * 4) Audit Source Identification - using which server
 * 5) Participant Object Identification - to what record
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Audit implements Reportable {

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
     * Security domain who triggered the action
     */
    private String domain;

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
     * Indicates whether the event succeeded or failed
     */
    private AuditOutcome outcome;

    /**
     * The date when the event was logged
     */
    private Instant timestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String domain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public AuditAccessPoint getAccessPoint() {
        return accessPoint;
    }

    public void setAccessPoint(AuditAccessPoint accessPoint) {
        this.accessPoint = accessPoint;
    }

    public AuditEntity getActor() {
        return actor;
    }

    public void setActor(AuditEntity actor) {
        this.actor = actor;
    }

    public AuditEntity getTarget() {
        return target;
    }

    public void setTarget(AuditEntity target) {
        this.target = target;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }
}
