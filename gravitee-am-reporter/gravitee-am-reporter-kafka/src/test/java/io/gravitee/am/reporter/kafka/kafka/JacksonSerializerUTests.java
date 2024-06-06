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
package io.gravitee.am.reporter.kafka.kafka;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.DummyNode;
import io.gravitee.am.reporter.kafka.audit.DtoMapper;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.node.api.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class JacksonSerializerUTests {

  @Test
  void Should_SerializeAnAuditAsJson() throws URISyntaxException, IOException {
    DtoMapper mapper = new DtoMapper();
    GraviteeContext context = GraviteeContext.defaultContext("acme");
    Node node = new DummyNode("node-id", "main.srv.local");
    AuditMessageValueDto auditMessageValueDto = mapper
        .map(AuditValueFactory.createAudit(), context, node);

    byte[] bytes;
    try (JacksonSerializer<AuditMessageValueDto> serializer = new JacksonSerializer<>()) {
        bytes = serializer.serialize("topic", auditMessageValueDto);
    }

    URL resource = JacksonSerializerUTests.class.getResource("/json/audit.json");
    byte[] expected = Files.readAllBytes(Paths.get(resource.toURI()));
    assertEquals(new String(expected, StandardCharsets.UTF_8), new String(bytes, StandardCharsets.UTF_8));
  }
}
