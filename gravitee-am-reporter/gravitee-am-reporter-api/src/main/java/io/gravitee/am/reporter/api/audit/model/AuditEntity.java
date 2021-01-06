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

import io.gravitee.am.model.ReferenceType;

import java.util.Map;

/**
 * See <a href="https://tools.ietf.org/html/rfc3881#section-5.2">5.2. Active Participant Identification</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditEntity {

    /**
     * Unique identifier for the entity actively participating in the event
     */
    private String id;

    /**
     * Alternative unique identifier for the entity
     */
    private String alternativeId;

    /**
     * Entity type (User, Client, Domain, etc ...)
     */
    private String type;

    /**
     * The human-meaningful name for the entity
     */
    private String displayName;

    /**
     * The reference type of the entity (ex : DOMAIN, ORGANIZATION).
     */
    private ReferenceType referenceType;

    /**
     * The reference id of the entity (ex : domain identifier, organization identifier).
     */
    private String referenceId;

    private Map<String, Object> attributes;

    public AuditEntity() {
    }

    public AuditEntity(AuditEntity other) {
        this.id = other.id;
        this.alternativeId = other.alternativeId;
        this.type = other.type;
        this.displayName = other.displayName;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.attributes = other.attributes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlternativeId() {
        return alternativeId;
    }

    public void setAlternativeId(String alternativeId) {
        this.alternativeId = alternativeId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
