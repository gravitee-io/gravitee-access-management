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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;

import java.time.Instant;
import java.util.Arrays;

import static com.fasterxml.jackson.core.JsonToken.VALUE_EMBEDDED_OBJECT;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AuditBuilder<T> {

    private String id;
    private String transactionalId;
    private Instant timestamp;
    private String domain;
    private String accessPointId;
    private String accessPointAlternativeId;
    private String accessPointName;
    private String actorDomain;
    private String type;
    private Throwable throwable;
    private String actorId;
    private String actorType;
    private String actorAlternativeId;
    private String actorDisplayName;
    private String targetId;
    private String targetType;
    private String targetAlternativeId;
    private String targetDisplayName;
    private String targetDomain;
    private String ipAddress;
    private String userAgent;
    private Object oldValue;
    private Object newValue;

    public static <T> T builder(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public AuditBuilder() {
        id = RandomString.generate();
        transactionalId = RandomString.generate();
        timestamp = Instant.now();
    }

    public T transactionalId(String transactionalId) {
        this.transactionalId = transactionalId;
        return (T) this;
    }

    public T domain(String domain) {
        this.domain = domain;
        return (T) this;
    }

    public T client(String client) {
        this.accessPointAlternativeId = client;
        return (T) this;
    }

    public T client(Client client) {
        this.accessPointId = client.getId();
        this.accessPointAlternativeId = client.getClientId();
        this.accessPointName = client.getClientName();
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
        this.oldValue = oldValue;
        return (T) this;
    }

    protected String getType() {
        return type;
    }

    protected void setActor(String actorId, String actorType, String actorAlternativeId, String actorDisplayName, String actorDomain) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.actorAlternativeId = actorAlternativeId;
        this.actorDisplayName = actorDisplayName;
        this.actorDomain = actorDomain;
    }

    protected void setTarget(String targetId, String targetType, String targetAlternativeId, String targetDisplayName, String targetDomain) {
        this.targetId = targetId;
        this.targetType = targetType;
        this.targetAlternativeId = targetAlternativeId;
        this.targetDisplayName = targetDisplayName;
        this.targetDomain = targetDomain;
    }

    protected void setNewValue(Object newValue) {
        this.newValue = newValue;
    }

    public Audit build(ObjectMapper mapper) {
        Audit audit = new Audit();
        audit.setId(id);
        audit.setTransactionId(transactionalId);
        audit.setDomain(domain);
        audit.setType(type);
        audit.setTimestamp(timestamp);

        // The actor and/or target of an event is dependent on the action performed.
        // All events have actors but not all have targets.
        AuditEntity actor = new AuditEntity();
        actor.setId(actorId);
        actor.setType(actorType);
        actor.setAlternativeId(actorAlternativeId);
        actor.setDisplayName(actorDisplayName);
        actor.setDomain(actorDomain);
        audit.setActor(actor);

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
            AuditEntity target = new AuditEntity();
            target.setId(targetId);
            target.setType(targetType);
            target.setAlternativeId(targetAlternativeId);
            target.setDisplayName(targetDisplayName);
            target.setDomain(targetDomain);
            audit.setTarget(target);
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
                        ((ArrayNode) newNode).add(((ObjectNode) jsonNode).remove(Arrays.asList("updatedAt", "createdAt", "expiresAt", "userId", "domain")));
                    });
                } else {
                    oldNode = oldValue == null
                            ? mapper.createObjectNode()
                            : mapper.convertValue(oldValue, ObjectNode.class).remove(Arrays.asList("updatedAt", "createdAt", "lastEvent"));
                    newNode = newValue == null
                            ? mapper.createObjectNode()
                            : mapper.convertValue(newValue, ObjectNode.class).remove(Arrays.asList("updatedAt", "createdAt", "lastEvent"));
                }
                clean(oldNode, null, null);
                clean(newNode, null, null);
                result.setMessage(JsonDiff.asJson(oldNode, newNode).toString());
            }
        } else {
            result.setStatus(Status.FAILURE);
            result.setMessage(throwable.getMessage());
        }
        audit.setOutcome(result);

        return audit;
    }

    /**
     * Some object have VALUE_EMBEDDED_OBJECT Json Token which are not supported by the JsonDiff lib
     */
    private static void clean(JsonNode current, JsonNode parent, String fieldName) {
        if (VALUE_EMBEDDED_OBJECT.equals(current.asToken())) {
            ((ObjectNode) parent).put(fieldName, current.asText());
        }

        for (JsonNode child : current) {
            if(child.isObject()) {
                parent = child;
                if (child.fieldNames() != null && child.fieldNames().hasNext()) {
                    fieldName = child.fieldNames().next();
                }
            }
            clean(child, parent, fieldName);
        }
    }
}
