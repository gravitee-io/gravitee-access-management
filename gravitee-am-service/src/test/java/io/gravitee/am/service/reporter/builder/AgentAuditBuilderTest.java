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
import org.junit.jupiter.api.Test;

import static io.gravitee.am.common.audit.EventType.AGENT_AUTHENTICATED;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAuditBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefaultEventType() {
        var audit = AuditBuilder.builder(AgentAuditBuilder.class).build(objectMapper);
        assertEquals(AGENT_AUTHENTICATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldNotFailOnNullFields() {
        var audit = AuditBuilder.builder(AgentAuditBuilder.class)
                .blueprintId(null)
                .blueprintName(null)
                .agentInstanceId(null)
                .agentType(null)
                .assertionKid(null)
                .assertionIss(null)
                .assertionJti(null)
                .build(objectMapper);
        assertEquals(AGENT_AUTHENTICATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldPopulateAgentFieldsIntoMessage() {
        var audit = AuditBuilder.builder(AgentAuditBuilder.class)
                .blueprintId("blueprint-123")
                .blueprintName("my-blueprint")
                .agentInstanceId("instance-abc")
                .agentType("AUTONOMOUS")
                .assertionKid("kid-1")
                .assertionIss("iss-1")
                .assertionJti("jti-1")
                .build(objectMapper);

        String message = audit.getOutcome().getMessage();
        assertNotNull(message);
        assertTrue(message.contains("blueprint-123"));
        assertTrue(message.contains("my-blueprint"));
        assertTrue(message.contains("instance-abc"));
        assertTrue(message.contains("AUTONOMOUS"));
        assertTrue(message.contains("kid-1"));
        assertTrue(message.contains("iss-1"));
        assertTrue(message.contains("jti-1"));
    }

    @Test
    void shouldReportFailureWhenThrowableSet() {
        var audit = AuditBuilder.builder(AgentAuditBuilder.class)
                .blueprintId("blueprint-x")
                .throwable(new RuntimeException("boom"))
                .build(objectMapper);

        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals("boom", audit.getOutcome().getMessage());
    }
}
