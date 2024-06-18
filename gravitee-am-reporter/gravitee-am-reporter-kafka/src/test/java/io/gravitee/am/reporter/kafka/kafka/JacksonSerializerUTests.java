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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.audit.DtoMapper;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.node.api.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;

public class JacksonSerializerUTests {

    @Test
    void Should_SerializeAnAuditAsJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        GraviteeContext context = GraviteeContext.defaultContext("acme");
        Node node = Mockito.mock(Node.class);
        when(node.hostname()).thenReturn("main.srv.local");
        when(node.id()).thenReturn("node-id");
        AuditMessageValueDto auditMessageValueDto = DtoMapper.map(AuditValueFactory.createAudit(), context, node);

        byte[] bytes;
        try (JacksonSerializer<AuditMessageValueDto> serializer = new JacksonSerializer<>()) {
            bytes = serializer.serialize("topic", auditMessageValueDto);
        }

        URL resource = JacksonSerializerUTests.class.getResource("/json/audit.json");
        String flattenResource = mapper.writeValueAsString(mapper.readValue(resource, Object.class));
        Assertions.assertEquals(flattenResource, new String(bytes, StandardCharsets.UTF_8));
    }
}
