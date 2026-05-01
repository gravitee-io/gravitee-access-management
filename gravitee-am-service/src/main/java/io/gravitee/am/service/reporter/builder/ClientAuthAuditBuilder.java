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
import com.google.common.collect.ImmutableMap;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oauth2.ClientIds;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import java.util.HashMap;
import java.util.Map;

public class ClientAuthAuditBuilder extends GatewayAuditBuilder<ClientAuthAuditBuilder> {

    private static final String CIMD_METADATA_DOCUMENT_HASH_KEY = "metadataDocumentHash";

    private String metadataDocumentHash;
    private Map<String, Object> agentAttributes;

    public ClientAuthAuditBuilder() {
        super();
        type(EventType.CLIENT_AUTHENTICATION);
    }

    public ClientAuthAuditBuilder clientActor(Client client) {
        if (client != null) {
            var domainId = client.getDomain();
            var alternativeId = ClientIds.isUrlShaped(client.getClientId()) ? client.getClientId() : client.getClientName();
            setActor(client.getId(), EntityType.APPLICATION, alternativeId, client.getClientName(), ReferenceType.DOMAIN, domainId);
            super.client(client);
            if (domainId != null) {
                reference(Reference.domain(domainId));
            }
            this.metadataDocumentHash = client.getCimdMetadataHash();
        }
        return this;
    }

    public ClientAuthAuditBuilder agentContext(Client client) {
        if (client != null && client.isAgentApplication()) {
            agentAttributes = new HashMap<>();
            agentAttributes.put("agentInstanceId", client.getAgentInstanceId());
            if (client.getAgentType() != null) {
                agentAttributes.put("agentType", client.getAgentType().name());
            }
        }
        return this;
    }

    @Override
    protected AuditEntity createActor() {
        AuditEntity actor = super.createActor();
        if (metadataDocumentHash != null) {
            HashMap<String, Object> attributes =
                    actor.getAttributes() == null ? new HashMap<>() : new HashMap<>(actor.getAttributes());
            attributes.put(CIMD_METADATA_DOCUMENT_HASH_KEY, metadataDocumentHash);
            actor.setAttributes(ImmutableMap.copyOf(attributes));
        }
        return actor;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        if (agentAttributes != null && !agentAttributes.isEmpty()) {
            setNewValue(agentAttributes);
        }
        return super.build(mapper);
    }
}
