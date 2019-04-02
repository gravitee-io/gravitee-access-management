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
package io.gravitee.am.reporter.mongodb.audit.model;

import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditMongo {

    @BsonId
    private String id;
    private String transactionId;
    private String type;
    private String domain;
    private AuditAccessPointMongo accessPoint;
    private AuditEntityMongo actor;
    private AuditEntityMongo target;
    private AuditOutcomeMongo outcome;
    private Instant timestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public AuditAccessPointMongo getAccessPoint() {
        return accessPoint;
    }

    public void setAccessPoint(AuditAccessPointMongo accessPoint) {
        this.accessPoint = accessPoint;
    }

    public AuditEntityMongo getActor() {
        return actor;
    }

    public void setActor(AuditEntityMongo actor) {
        this.actor = actor;
    }

    public AuditEntityMongo getTarget() {
        return target;
    }

    public void setTarget(AuditEntityMongo target) {
        this.target = target;
    }

    public AuditOutcomeMongo getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcomeMongo outcome) {
        this.outcome = outcome;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
