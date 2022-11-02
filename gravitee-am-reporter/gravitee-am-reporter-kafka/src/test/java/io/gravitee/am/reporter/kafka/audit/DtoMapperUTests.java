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
import io.gravitee.am.reporter.kafka.DummyNode;
import io.gravitee.am.reporter.kafka.dto.AuditAccessPointDto;
import io.gravitee.am.reporter.kafka.dto.AuditEntityDto;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.node.api.Node;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class DtoMapperUTests {

  @Test
  public void Should_MapAuditToAuditDto() {
    // arrange
    DtoMapper mapper = new DtoMapper();
    GraviteeContext context = GraviteeContext.defaultContext("domain");
    Node nodeMock = new DummyNode("first", "first.srv.local");
    Audit audit = AuditValueFactory.createAudit();

    // act
    AuditMessageValueDto dto = mapper.map(audit, context, nodeMock);

    // assert
    Assert.assertEquals("id", dto.getId());
    Assert.assertEquals("reference id", dto.getReferenceId());
    this.assertEquals(ReferenceType.APPLICATION, dto.getReferenceType());
    Assert.assertEquals(Instant.EPOCH, dto.getTimestamp());
    Assert.assertEquals("transaction id", dto.getTransactionId());
    Assert.assertEquals("type", dto.getType());
    Assert.assertEquals("status", dto.getStatus());
    this.assertEquals(audit.getActor(), dto.getActor());
    this.assertEquals(audit.getTarget(), dto.getTarget());
    Assert.assertEquals(context.getOrganizationId(), dto.getOrganizationId());
    Assert.assertEquals(context.getEnvironmentId(), dto.getEnvironmentId());

    Assert.assertEquals("first", dto.getNodeId());
    Assert.assertEquals("first.srv.local", dto.getNodeHostname());

    this.assertEquals(audit.getAccessPoint(), dto.getAccessPoint());

  }

  @Test
  public void Should_MapReferenceTypeIntoString() {
    DtoMapper mapper = new DtoMapper();
    Assert.assertEquals("APPLICATION", mapper.map(ReferenceType.APPLICATION));
    Assert.assertEquals("DOMAIN", mapper.map(ReferenceType.DOMAIN));
    Assert.assertEquals("ENVIRONMENT", mapper.map(ReferenceType.ENVIRONMENT));
    Assert.assertEquals("ORGANIZATION", mapper.map(ReferenceType.ORGANIZATION));
    Assert.assertEquals("PLATFORM", mapper.map(ReferenceType.PLATFORM));
  }

  /**
   * helper method for assertion.
   *
   * @param expected expected
   * @param actual   actual
   */
  private void assertEquals(ReferenceType expected, String actual) {
    DtoMapper mapper = new DtoMapper();
    Assert.assertNotNull(expected);
    Assert.assertEquals(mapper.map(expected), actual);
  }

  @Test
  public void Should_MapAuditEntryIntoAuditEntryDto() {
    DtoMapper mapper = new DtoMapper();
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

    AuditEntityDto dto = mapper.map(auditEntity);

    Assert.assertEquals("type", dto.getType());
    Assert.assertEquals("id", dto.getId());
    Assert.assertEquals("reference id", dto.getReferenceId());
    Assert.assertEquals("DOMAIN", dto.getReferenceType());
    Assert.assertEquals("alternative id", dto.getAlternativeId());
    Assert.assertEquals("Display Name", dto.getDisplayName());

  }

  @Test
  public void Should_MapAuditEntryIntoAuditEntryDto_Null_ReferenceType() {
    DtoMapper mapper = new DtoMapper();
    AuditEntity auditEntity = new AuditEntity();
    auditEntity.setId(null);
    auditEntity.setType("type");
    auditEntity.setAlternativeId("alternative id");
    auditEntity.setReferenceId(null);
    auditEntity.setReferenceType(null);
    auditEntity.setDisplayName(null);
    auditEntity.setAttributes(null);

    AuditEntityDto dto = mapper.map(auditEntity);

    Assert.assertEquals("type", dto.getType());
    Assert.assertEquals("alternative id", dto.getAlternativeId());
    Assert.assertNull("id is null", dto.getId());
    Assert.assertNull("reference id is null", dto.getReferenceId());
    Assert.assertNull("reference type is null", dto.getReferenceType());
    Assert.assertNull("display name is null", dto.getDisplayName());
  }

  /**
   * helper method for assertion.
   *
   * @param expected expected
   * @param actual   actual
   */
  private void assertEquals(AuditEntity expected, AuditEntityDto actual) {
    // expected must be full filled else test is not relevant
    Assert.assertNotNull(expected);
    Assert.assertNotNull(expected.getType());
    Assert.assertNotNull(expected.getId());
    Assert.assertNotNull(expected.getReferenceId());
    Assert.assertNotNull(expected.getAlternativeId());
    Assert.assertNotNull(expected.getDisplayName());

    // real assertions
    Assert.assertEquals(expected.getType(), actual.getType());
    Assert.assertEquals(expected.getId(), actual.getId());
    Assert.assertEquals(expected.getReferenceId(), actual.getReferenceId());
    this.assertEquals(expected.getReferenceType(), actual.getReferenceType());
    Assert.assertEquals(expected.getAlternativeId(), actual.getAlternativeId());
    Assert.assertEquals(expected.getDisplayName(), actual.getDisplayName());
  }

  @Test
  public void Should_MapAuditAccessPointIntoAuditAccessPointDto() {
    DtoMapper mapper = new DtoMapper();
    AuditAccessPoint auditAccessPoint = new AuditAccessPoint();
    auditAccessPoint.setId("id");
    auditAccessPoint.setAlternativeId("alternative id");
    auditAccessPoint.setDisplayName("Display Name");
    auditAccessPoint.setIpAddress("10.0.0.1");
    auditAccessPoint.setUserAgent("Chrome");

    AuditAccessPointDto dto = mapper.map(auditAccessPoint);
    Assert.assertEquals("id", dto.getId());
    Assert.assertEquals("alternative id", dto.getAlternativeId());
    Assert.assertEquals("Display Name", dto.getDisplayName());
    Assert.assertEquals("10.0.0.1", dto.getIpAddress());
    Assert.assertEquals("Chrome", dto.getUserAgent());
  }

  @Test
  public void Should_MapAuditAccessPointIntoAuditAccessPointDto_When_IpIsInvalid() {
    DtoMapper mapper = new DtoMapper();
    AuditAccessPoint auditAccessPoint = new AuditAccessPoint();
    auditAccessPoint.setId("id");
    auditAccessPoint.setAlternativeId("alternative id");
    auditAccessPoint.setDisplayName("Display Name");
    auditAccessPoint.setIpAddress("azertyuiop");
    auditAccessPoint.setUserAgent("Chrome");

    AuditAccessPointDto dto = mapper.map(auditAccessPoint);
    Assert.assertEquals("id", dto.getId());
    Assert.assertEquals("alternative id", dto.getAlternativeId());
    Assert.assertEquals("Display Name", dto.getDisplayName());
    Assert.assertEquals("0.0.0.0", dto.getIpAddress());
    Assert.assertEquals("Chrome", dto.getUserAgent());
  }

  /**
   * helper method for assertion.
   *
   * @param expected expected
   * @param actual   actual
   */
  private void assertEquals(AuditAccessPoint expected, AuditAccessPointDto actual) {
    // expected must be full filled else test is not relevant
    Assert.assertNotNull(expected);
    Assert.assertNotNull(expected.getId());
    Assert.assertNotNull(expected.getIpAddress());
    Assert.assertNotNull(expected.getUserAgent());
    Assert.assertNotNull(expected.getAlternativeId());
    Assert.assertNotNull(expected.getDisplayName());

    // real assertions
    Assert.assertEquals(expected.getId(), actual.getId());
    Assert.assertEquals(expected.getIpAddress(), actual.getIpAddress());
    Assert.assertEquals(expected.getDisplayName(), actual.getDisplayName());
    Assert.assertEquals(expected.getAlternativeId(), actual.getAlternativeId());
    Assert.assertEquals(expected.getDisplayName(), actual.getDisplayName());
  }

}