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
package io.gravitee.am.reporter.kafka.dto;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.node.api.Node;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Copied from File reporter and renamed to match Kafka Semantics
 *
 * @author Florent Amaridon
 * @author Visiativ
 */
@Setter
@Getter
@Builder
public class AuditMessageValueDto {

    private String id;
    private String transactionId;
    private String type;
    private String referenceType;
    private String referenceId;
    private AuditAccessPointDto accessPoint;
    private AuditEntityDto actor;
    private AuditEntityDto target;
    private AuditOutcomeDto outcome;
    private Instant timestamp;
    private String environmentId;
    private String organizationId;
    private String domainId;
    private String nodeId;
    private String nodeHostname;

    /**
     * The extra attributes this reporter was configured to export, keyed by the operator's chosen name;
     * values that are not already strings are serialized as JSON
     */
    private Map<String, String> customAttributes;

    /**
     * @deprecated moved into the {@link #outcome} field
     */
    @Deprecated(since = "4.5", forRemoval = true)
    public String getStatus() {
        return outcome == null ? null : outcome.status();
    }

    public static AuditMessageValueDto from(Audit audit, GraviteeContext context, Node node) {
        return builder()
                .id(audit.getId())
                .referenceId(audit.getReferenceId())
                .referenceType(Objects.toString(audit.getReferenceType()))
                .timestamp(audit.timestamp())
                .transactionId(audit.getTransactionId())
                .type(audit.getType())
                .accessPoint(AuditAccessPointDto.from(audit.getAccessPoint()))
                .actor(AuditEntityDto.from(audit.getActor()))
                .target(AuditEntityDto.from(audit.getTarget()))
                .outcome(AuditOutcomeDto.from(audit.getOutcome()))
                .customAttributes(asStringValues(audit.getCustomAttributes()))
                .context(context)
                .node(node)
                .build();
    }

    private static Map<String, String> asStringValues(Map<String, Object> customAttributes) {
        if (customAttributes == null || customAttributes.isEmpty()) {
            return null;
        }
        var mapper = new ObjectMapper();
        return customAttributes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    if (e.getValue() instanceof String value) {
                        return value;
                    }
                    try {
                        return mapper.writeValueAsString(e.getValue());
                    } catch (JsonProcessingException ex) {
                        return "";
                    }
                }));
    }

    @SuppressWarnings("unused") // additional methods for @lombok.Builder
    static class AuditMessageValueDtoBuilder {
        AuditMessageValueDtoBuilder context(GraviteeContext context) {
            if (context == null) {
                return this;
            }
            return organizationId(context.getOrganizationId())
                    .environmentId(context.getEnvironmentId())
                    .domainId(context.getDomainId());
        }

        AuditMessageValueDtoBuilder node(Node node) {
            if (node == null) {
                return this;
            }
            return nodeId(node.id())
                    .nodeHostname(node.hostname());
        }
    }


}
