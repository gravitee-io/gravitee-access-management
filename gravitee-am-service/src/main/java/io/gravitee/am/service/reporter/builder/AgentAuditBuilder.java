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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit builder for agent identity events (AGENT_AUTHENTICATED, AGENT_KEY_USED).
 *
 * @author GraviteeSource Team
 */
public class AgentAuditBuilder extends GatewayAuditBuilder<AgentAuditBuilder> {

    private final Map<String, Object> agentData = new HashMap<>();

    public AgentAuditBuilder() {
        super();
        type(EventType.AGENT_AUTHENTICATED);
    }

    public AgentAuditBuilder blueprintId(String blueprintId) {
        if (blueprintId != null) agentData.put("blueprintId", blueprintId);
        return this;
    }

    public AgentAuditBuilder blueprintName(String blueprintName) {
        if (blueprintName != null) agentData.put("blueprintName", blueprintName);
        return this;
    }

    public AgentAuditBuilder agentInstanceId(String agentInstanceId) {
        if (agentInstanceId != null) agentData.put("agentInstanceId", agentInstanceId);
        return this;
    }

    public AgentAuditBuilder agentType(String agentType) {
        if (agentType != null) agentData.put("agentType", agentType);
        return this;
    }

    public AgentAuditBuilder assertionKid(String kid) {
        if (kid != null) agentData.put("assertionKid", kid);
        return this;
    }

    public AgentAuditBuilder assertionIss(String iss) {
        if (iss != null) agentData.put("assertionIss", iss);
        return this;
    }

    public AgentAuditBuilder assertionJti(String jti) {
        if (jti != null) agentData.put("assertionJti", jti);
        return this;
    }

    public AgentAuditBuilder resolutionMethod(String method) {
        if (method != null) agentData.put("resolutionMethod", method);
        return this;
    }

    public AgentAuditBuilder cimdMetadataUri(String uri) {
        if (uri != null) agentData.put("cimdMetadataUri", uri);
        return this;
    }

    public AgentAuditBuilder cimdSoftwareId(String softwareId) {
        if (softwareId != null) agentData.put("cimdSoftwareId", softwareId);
        return this;
    }

    public AgentAuditBuilder keyUsed() {
        type(EventType.AGENT_KEY_USED);
        return this;
    }

    public AgentAuditBuilder keyAdded() {
        type(EventType.AGENT_KEY_ADDED);
        return this;
    }

    public AgentAuditBuilder keyRemoved() {
        type(EventType.AGENT_KEY_REMOVED);
        return this;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        if (!agentData.isEmpty()) {
            setNewValue(agentData);
        }
        return super.build(mapper);
    }
}
