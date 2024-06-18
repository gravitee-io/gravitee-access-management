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
package io.gravitee.am.reporter.kafka.audit;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.dto.AuditAccessPointDto;
import io.gravitee.am.reporter.kafka.dto.AuditEntityDto;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.node.api.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class DtoMapperUTests {

    @Test
    void Should_MapAuditToAuditDto() {
        // arrange
        Node node = Mockito.mock(Node.class);
        when(node.hostname()).thenReturn("first.srv.local");
        when(node.id()).thenReturn("first");
        GraviteeContext context = GraviteeContext.defaultContext("domain");
        Audit audit = AuditValueFactory.createAudit();

        // act
        AuditMessageValueDto dto = DtoMapper.map(audit, context, node);

        // assert
        Assertions.assertEquals("id", dto.getId());
        Assertions.assertEquals("reference id", dto.getReferenceId());
        this.assertEquals(ReferenceType.APPLICATION, dto.getReferenceType());
        Assertions.assertEquals(Instant.EPOCH, dto.getTimestamp());
        Assertions.assertEquals("transaction id", dto.getTransactionId());
        Assertions.assertEquals("type", dto.getType());
        Assertions.assertEquals("status", dto.getStatus());
        this.assertEquals(audit.getActor(), dto.getActor());
        this.assertEquals(audit.getTarget(), dto.getTarget());
        Assertions.assertEquals(context.getOrganizationId(), dto.getOrganizationId());
        Assertions.assertEquals(context.getEnvironmentId(), dto.getEnvironmentId());

        Assertions.assertEquals("first", dto.getNodeId());
        Assertions.assertEquals("first.srv.local", dto.getNodeHostname());

        this.assertEquals(audit.getAccessPoint(), dto.getAccessPoint());

    }

    @Test
    void Should_MapReferenceTypeIntoString() {
        Assertions.assertEquals("APPLICATION", DtoMapper.mapReferenceType(ReferenceType.APPLICATION));
        Assertions.assertEquals("DOMAIN", DtoMapper.mapReferenceType(ReferenceType.DOMAIN));
        Assertions.assertEquals("ENVIRONMENT", DtoMapper.mapReferenceType(ReferenceType.ENVIRONMENT));
        Assertions.assertEquals("ORGANIZATION", DtoMapper.mapReferenceType(ReferenceType.ORGANIZATION));
        Assertions.assertEquals("PLATFORM", DtoMapper.mapReferenceType(ReferenceType.PLATFORM));
    }

    /**
     * helper method for assertion.
     *
     * @param expected expected
     * @param actual   actual
     */
    private void assertEquals(ReferenceType expected, String actual) {

        assertNotNull(expected);
        Assertions.assertEquals(DtoMapper.mapReferenceType(expected), actual);
    }

    @Test
    void Should_MapAuditEntryIntoAuditEntryDto() {
        AuditEntity auditEntity = new AuditEntity();
        auditEntity.setId("id");
        auditEntity.setType("type");
        auditEntity.setReferenceId("reference id");
        auditEntity.setReferenceType(ReferenceType.DOMAIN);
        auditEntity.setAlternativeId("alternative id");
        auditEntity.setDisplayName("Display Name");
        Map<String, Object> attr = new HashMap<>();
        attr.put("a", "string");
        attr.put("b", 18);
        auditEntity.setAttributes(attr);

        AuditEntityDto dto = DtoMapper.mapAuditEntityDto(auditEntity);

        Assertions.assertEquals("type", dto.getType());
        Assertions.assertEquals("id", dto.getId());
        Assertions.assertEquals("reference id", dto.getReferenceId());
        Assertions.assertEquals("DOMAIN", dto.getReferenceType());
        Assertions.assertEquals("alternative id", dto.getAlternativeId());
        Assertions.assertEquals("Display Name", dto.getDisplayName());

    }

    @Test
    void Should_MapAuditEntryIntoAuditEntryDto_Null_ReferenceType() {
        AuditEntity auditEntity = new AuditEntity();
        auditEntity.setId(null);
        auditEntity.setType("type");
        auditEntity.setAlternativeId("alternative id");
        auditEntity.setReferenceId(null);
        auditEntity.setReferenceType(null);
        auditEntity.setDisplayName(null);
        auditEntity.setAttributes(null);

        AuditEntityDto dto = DtoMapper.mapAuditEntityDto(auditEntity);

        Assertions.assertEquals("type", dto.getType());
        Assertions.assertEquals("alternative id", dto.getAlternativeId());
        assertNull(dto.getId());
        assertNull(dto.getReferenceId());
        assertNull(dto.getReferenceType());
        assertNull(dto.getDisplayName());
    }

    /**
     * helper method for assertion.
     *
     * @param expected expected
     * @param actual   actual
     */
    private void assertEquals(AuditEntity expected, AuditEntityDto actual) {
        // expected must be full filled else test is not relevant
        assertNotNull(expected);
        assertNotNull(expected.getType());
        assertNotNull(expected.getId());
        assertNotNull(expected.getReferenceId());
        assertNotNull(expected.getAlternativeId());
        assertNotNull(expected.getDisplayName());

        // real assertions
        Assertions.assertEquals(expected.getType(), actual.getType());
        Assertions.assertEquals(expected.getId(), actual.getId());
        Assertions.assertEquals(expected.getReferenceId(), actual.getReferenceId());
        this.assertEquals(expected.getReferenceType(), actual.getReferenceType());
        Assertions.assertEquals(expected.getAlternativeId(), actual.getAlternativeId());
        Assertions.assertEquals(expected.getDisplayName(), actual.getDisplayName());
    }

    @Test
    void Should_MapAuditAccessPointIntoAuditAccessPointDto() {
        AuditAccessPoint auditAccessPoint = new AuditAccessPoint();
        auditAccessPoint.setId("id");
        auditAccessPoint.setAlternativeId("alternative id");
        auditAccessPoint.setDisplayName("Display Name");
        auditAccessPoint.setIpAddress("10.0.0.1");
        auditAccessPoint.setUserAgent("Chrome");

        AuditAccessPointDto dto = DtoMapper.mapAuditAccessPoint(auditAccessPoint);
        Assertions.assertEquals("id", dto.getId());
        Assertions.assertEquals("alternative id", dto.getAlternativeId());
        Assertions.assertEquals("Display Name", dto.getDisplayName());
        Assertions.assertEquals("10.0.0.1", dto.getIpAddress());
        Assertions.assertEquals("Chrome", dto.getUserAgent());
    }

    @Test
    void Should_MapAuditAccessPointIntoAuditAccessPointDto_When_IpIsInvalid() {
        AuditAccessPoint auditAccessPoint = new AuditAccessPoint();
        auditAccessPoint.setId("id");
        auditAccessPoint.setAlternativeId("alternative id");
        auditAccessPoint.setDisplayName("Display Name");
        auditAccessPoint.setIpAddress("azertyuiop");
        auditAccessPoint.setUserAgent("Chrome");

        AuditAccessPointDto dto = DtoMapper.mapAuditAccessPoint(auditAccessPoint);
        Assertions.assertEquals("id", dto.getId());
        Assertions.assertEquals("alternative id", dto.getAlternativeId());
        Assertions.assertEquals("Display Name", dto.getDisplayName());
        Assertions.assertEquals("0.0.0.0", dto.getIpAddress());
        Assertions.assertEquals("Chrome", dto.getUserAgent());
    }

    /**
     * helper method for assertion.
     *
     * @param expected expected
     * @param actual   actual
     */
    private void assertEquals(AuditAccessPoint expected, AuditAccessPointDto actual) {
        // expected must be full filled else test is not relevant
        assertNotNull(expected);
        assertNotNull(expected.getId());
        assertNotNull(expected.getIpAddress());
        assertNotNull(expected.getUserAgent());
        assertNotNull(expected.getAlternativeId());
        assertNotNull(expected.getDisplayName());

        // real assertions
        Assertions.assertEquals(expected.getId(), actual.getId());
        Assertions.assertEquals(expected.getIpAddress(), actual.getIpAddress());
        Assertions.assertEquals(expected.getDisplayName(), actual.getDisplayName());
        Assertions.assertEquals(expected.getAlternativeId(), actual.getAlternativeId());
        Assertions.assertEquals(expected.getDisplayName(), actual.getDisplayName());
    }

}
