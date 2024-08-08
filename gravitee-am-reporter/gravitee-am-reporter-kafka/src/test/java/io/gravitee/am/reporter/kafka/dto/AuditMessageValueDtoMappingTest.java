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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.common.component.Lifecycle;
import io.gravitee.node.api.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

class AuditMessageValueDtoMappingTest {

    @Test
    void shouldMapCorrectlyFromAudit() {
        var node = testNode();
        var ctx = GraviteeContext.defaultContext("test-domain");
        Audit audit = AuditValueFactory.createAudit();

        AuditMessageValueDto dto = AuditMessageValueDto.from(audit, ctx, node);
        // check node & context data is carried over
        assertThat(dto)
                .satisfies(d -> {
                    assertThat(d.getDomainId()).isEqualTo(ctx.getDomainId());
                    assertThat(d.getOrganizationId()).isEqualTo(ctx.getOrganizationId());
                    assertThat(d.getEnvironmentId()).isEqualTo(ctx.getEnvironmentId());
                    assertThat(d.getNodeId()).isEqualTo(node.id());
                    assertThat(d.getNodeHostname()).isEqualTo(node.hostname());
                });
        // check all other fields
        assertThat(dto).usingRecursiveComparison()
                .ignoringFields("domainId", "organizationId", "environmentId", "nodeId", "nodeHostname")
                .withEnumStringComparison()
                .isEqualTo(audit);
    }

    @ParameterizedTest
    @EnumSource(ReferenceType.class)
    void shouldMapReferenceTypeToExpectedString(ReferenceType referenceType) {
        var audit = new Audit();
        audit.setReferenceType(referenceType);
        AuditMessageValueDto dto = AuditMessageValueDto.from(audit, null, null);
        assertThat(dto.getReferenceType())
                .isEqualTo(referenceType.toString().toUpperCase(Locale.ROOT));
    }

    @Test
    void shouldMapAdditionalAttributes() {
        var auditEntity = new AuditEntity();
        auditEntity.setId("id");
        auditEntity.setAlternativeId("alternativeId");
        auditEntity.setDisplayName("displayName");
        auditEntity.setReferenceId("referenceId");
        auditEntity.setReferenceType(ReferenceType.APPLICATION);
        auditEntity.setAttributes(Map.of("a", "1", "b", "two"));
        assertThat(AuditEntityDto.from(auditEntity))
                .usingRecursiveComparison()
                .withEnumStringComparison()
                .isEqualTo(auditEntity);
    }

    @Test
    void shouldIntInMapAdditionalAttributeBeParsed() {
        var auditEntity = new AuditEntity();
        var value = 1;
        var key = "a";
        auditEntity.setAttributes(Map.of(key, value, "b", "two"));
        AuditEntityDto from = AuditEntityDto.from(auditEntity);
        assertThat(from.attributes().get(key)).isEqualTo(Integer.toString(value));
    }

    @Test
    void shouldNotParsableInMapAdditionalAttributesBeReplaced() {
        var auditEntity = new AuditEntity();
        var key = "a";
        auditEntity.setAttributes(Map.of(key, mock(Object.class), "b", "two"));
        AuditEntityDto from = AuditEntityDto.from(auditEntity);
        assertThat(from.attributes().get(key)).isEqualTo("");
    }

    private Node testNode() {
        return new Node() {
            @Override
            public String hostname() {
                return "test-host";
            }

            @Override
            public String id() {
                return "test-node";
            }

            @Override
            public String name() {
                return "test-node-1";
            }

            @Override
            public String application() {
                return "test-app";
            }

            @Override
            public Lifecycle.State lifecycleState() {
                return Lifecycle.State.STARTED;
            }

            @Override
            public Node start() {
                return this;
            }

            @Override
            public Node stop() {
                return this;
            }
        };
    }
}
