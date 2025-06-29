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
package io.gravitee.am.service.reporter.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.common.collect.ImmutableMap;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;

import static com.fasterxml.jackson.core.JsonToken.VALUE_EMBEDDED_OBJECT;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AuditBuilder<T extends AuditBuilder<T>> {

    protected static final String UPDATED_AT = "updatedAt";
    protected static final String CREATED_AT = "createdAt";
    protected static final String EXTERNAL_ID = "externalId";
    protected static final String SOURCE_ID = "sourceId";
    protected final String id;
    protected String transactionalId;
    protected final Instant timestamp;
    protected ReferenceType referenceType;
    protected String referenceId;
    protected String accessPointId;
    protected String accessPointAlternativeId;
    protected String accessPointName;
    protected String type;
    protected Throwable throwable;
    protected String actorId;
    protected String actorType;
    protected String actorAlternativeId;
    protected String actorDisplayName;
    protected ReferenceType actorReferenceType;
    protected String actorReferenceId;
    protected String actorExternalId;
    protected String actorSourceId;
    protected String targetId;
    protected String targetType;
    protected String targetAlternativeId;
    protected String targetDisplayName;
    protected ReferenceType targetReferenceType;
    protected String targetReferenceId;
    protected String targetExternalId;
    protected String targetSourceId;
    protected String ipAddress;
    protected String userAgent;
    protected Object oldValue;
    protected Object newValue;

    public static <T> T builder(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected AuditBuilder() {
        id = RandomString.generate();
        transactionalId = RandomString.generate();
        timestamp = Instant.now();
    }

    public T transactionalId(String transactionalId) {
        this.transactionalId = transactionalId;
        return (T) this;
    }

    @Deprecated
    public T referenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        return (T) this;
    }

    @Deprecated
    public T referenceId(String referenceId) {
        this.referenceId = referenceId;
        return (T) this;
    }

    public T reference(Reference reference) {
        return referenceType(reference.type())
                .referenceId(reference.id());
    }

    public T client(String client) {
        this.accessPointAlternativeId = client;
        return (T) this;
    }

    public T client(Client client) {
        if (client != null) {
            this.accessPointId = client.getId();
            this.accessPointAlternativeId = client.getClientId();
            this.accessPointName = client.getClientName();
        }
        return (T) this;
    }

    public T type(String type) {
        this.type = type;
        return (T) this;
    }

    public T throwable(Throwable throwable) {
        this.throwable = throwable;
        return (T) this;
    }

    public T ipAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return (T) this;
    }

    public T userAgent(String userAgent) {
        this.userAgent = userAgent;
        return (T) this;
    }

    public T oldValue(Object oldValue) {
        this.oldValue = removeSensitiveData(oldValue);
        return (T) this;
    }

    public T principal(User principal) {
        if (principal != null) {
            setActor(principal.getId(), EntityType.USER, principal.getUsername(), getDisplayName(principal), getReferenceType(principal), getReferenceId(principal));
            if (principal.getAdditionalInformation() != null) {
                if (principal.getAdditionalInformation().containsKey(Claims.IP_ADDRESS)) {
                    ipAddress((String) principal.getAdditionalInformation().get(Claims.IP_ADDRESS));
                }
                if (principal.getAdditionalInformation().containsKey(Claims.USER_AGENT)) {
                    userAgent((String) principal.getAdditionalInformation().get(Claims.USER_AGENT));
                }
            }
        }
        return (T) this;
    }

    private String getDisplayName(User user) {
        return user.getAdditionalInformation() != null && user.getAdditionalInformation().containsKey(StandardClaims.NAME) ?
                (String) user.getAdditionalInformation().get(StandardClaims.NAME) :
                // default to username
                user.getUsername();
    }

    private ReferenceType getReferenceType(User user) {
        if (user.getAdditionalInformation() == null) {
            return null;
        }

        if (user.getAdditionalInformation().containsKey(Claims.DOMAIN)) {
            return ReferenceType.DOMAIN;
        }

        if (user.getAdditionalInformation().containsKey(Claims.ORGANIZATION)) {
            return ReferenceType.ORGANIZATION;
        }

        return null;
    }

    private String getReferenceId(User user) {

        if (user.getAdditionalInformation() == null) {
            return null;
        }

        if (user.getAdditionalInformation().containsKey(Claims.DOMAIN)) {
            return (String) user.getAdditionalInformation().get(Claims.DOMAIN);
        }

        if (user.getAdditionalInformation().containsKey(Claims.ORGANIZATION)) {
            return (String) user.getAdditionalInformation().get(Claims.ORGANIZATION);
        }

        return null;
    }

    protected String getType() {
        return type;
    }

    protected void setActor(String actorId, String actorType, String actorAlternativeId, String actorDisplayName, ReferenceType actorReferenceType, String actorReferenceId) {
        setActor(actorId, actorType, actorAlternativeId, actorDisplayName, actorReferenceType, actorReferenceId, null, null);
    }

    protected void setActor(String actorId, String actorType, String actorAlternativeId, String actorDisplayName, ReferenceType actorReferenceType, String actorReferenceId, String actorExternalId, String actorSourceId) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.actorAlternativeId = actorAlternativeId;
        this.actorDisplayName = actorDisplayName;
        this.actorReferenceType = actorReferenceType;
        this.actorReferenceId = actorReferenceId;
        this.actorExternalId = actorExternalId;
        this.actorSourceId = actorSourceId;
    }

    protected void setTarget(String targetId, String targetType, String targetAlternativeId, String targetDisplayName, ReferenceType targetReferenceType, String targetReferenceId) {
        setTarget(targetId, targetType, targetAlternativeId, targetDisplayName, targetReferenceType, targetReferenceId, null, null);
    }

    protected void setTarget(String targetId, String targetType, String targetAlternativeId, String targetDisplayName, ReferenceType targetReferenceType, String targetReferenceId, String targetExternalId, String targetSourceId) {
        this.targetId = targetId;
        this.targetType = targetType;
        this.targetAlternativeId = targetAlternativeId;
        this.targetDisplayName = targetDisplayName;
        this.targetReferenceType = targetReferenceType;
        this.targetReferenceId = targetReferenceId;
        this.targetExternalId = targetExternalId;
        this.targetSourceId = targetSourceId;
    }

    protected void setNewValue(Object newValue) {
        this.newValue = removeSensitiveData(newValue);
    }

    protected Object removeSensitiveData(Object value) {
        return value;
    }

    public Audit build(ObjectMapper mapper) {
        Audit audit = new Audit();
        audit.setId(id);
        audit.setTransactionId(transactionalId);
        audit.setReferenceType(referenceType);
        audit.setReferenceId(referenceId);
        audit.setType(type);
        audit.setTimestamp(timestamp);

        // The actor and/or target of an event is dependent on the action performed.
        // All events have actors but not all have targets.
        audit.setActor(createActor());

        // Network access point
        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId(accessPointId);
        accessPoint.setAlternativeId(accessPointAlternativeId);
        accessPoint.setDisplayName(accessPointName);
        accessPoint.setIpAddress(ipAddress);
        accessPoint.setUserAgent(userAgent);
        audit.setAccessPoint(accessPoint);

        // target
        if (targetId != null) {
            audit.setTarget(createTarget());
        }

        // result
        AuditOutcome result = new AuditOutcome();
        if (throwable == null) {
            result.setStatus(Status.SUCCESS);

            // set details
            if (newValue != null || oldValue != null) {
                ContainerNode oldNode;
                ContainerNode newNode;
                if (EventType.USER_CONSENT_CONSENTED.equals(type) || EventType.USER_CONSENT_REVOKED.equals(type)) {
                    oldNode = mapper.createArrayNode();
                    newNode = mapper.createArrayNode();
                    mapper.convertValue(newValue, ArrayNode.class).forEach(jsonNode -> {
                        ((ArrayNode) newNode).add(((ObjectNode) jsonNode).remove(Arrays.asList(UPDATED_AT, CREATED_AT, "expiresAt", "userId", "domain")));
                    });
                } else {
                    oldNode = oldValue == null
                            ? mapper.createObjectNode()
                            : mapper.convertValue(oldValue, ObjectNode.class).remove(Arrays.asList(UPDATED_AT, CREATED_AT, "lastEvent"));
                    newNode = newValue == null
                            ? mapper.createObjectNode()
                            : mapper.convertValue(newValue, ObjectNode.class).remove(Arrays.asList(UPDATED_AT, CREATED_AT, "lastEvent"));
                }
                clean(oldNode, null, null);
                clean(newNode, null, null);
                result.setMessage(JsonDiff.asJson(oldNode, newNode).toString());
            }
        } else {
            result.setStatus(Status.FAILURE);
            result.setMessage(throwable.getMessage() + (throwable.getCause() != null ? ". Cause: " + throwable.getCause().getMessage() : ""));
        }
        audit.setOutcome(result);

        return audit;
    }

    protected AuditEntity createActor() {
        AuditEntity actor = new AuditEntity();
        actor.setId(actorId);
        actor.setType(actorType);
        actor.setAlternativeId(actorAlternativeId);
        actor.setDisplayName(actorDisplayName);
        actor.setReferenceType(actorReferenceType);
        actor.setReferenceId(actorReferenceId);
        var actorAttributes = new HashMap<String, Object>();
        ofNullable(actorExternalId).ifPresent(v -> actorAttributes.put(EXTERNAL_ID, v));
        ofNullable(actorSourceId).ifPresent(v -> actorAttributes.put(SOURCE_ID, v));
        actor.setAttributes(ImmutableMap.copyOf(actorAttributes));
        return actor;
    }

    protected AuditEntity createTarget(){
        AuditEntity target = new AuditEntity();
        target.setId(targetId);
        target.setType(targetType);
        target.setAlternativeId(targetAlternativeId);
        target.setDisplayName(targetDisplayName);
        target.setReferenceType(targetReferenceType);
        target.setReferenceId(targetReferenceId);
        var targetAttributes = new HashMap<String, Object>();
        ofNullable(targetExternalId).ifPresent(v -> targetAttributes.put(EXTERNAL_ID, v));
        ofNullable(targetSourceId).ifPresent(v -> targetAttributes.put(SOURCE_ID, v));
        target.setAttributes(ImmutableMap.copyOf(targetAttributes));
        return target;
    }

    /**
     * Some object have VALUE_EMBEDDED_OBJECT Json Token which are not supported by the JsonDiff lib
     */
    private static void clean(JsonNode current, JsonNode parent, String fieldName) {
        if (VALUE_EMBEDDED_OBJECT.equals(current.asToken())) {
            ((ObjectNode) parent).put(fieldName, current.asText());
        }

        for (JsonNode child : current) {
            if (child.isObject()) {
                parent = child;
                if (child.fieldNames() != null && child.fieldNames().hasNext()) {
                    fieldName = child.fieldNames().next();
                }
            }
            clean(child, parent, fieldName);
        }
    }
}
