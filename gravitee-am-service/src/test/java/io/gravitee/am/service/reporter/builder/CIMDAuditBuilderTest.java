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

import static io.gravitee.am.common.audit.EventType.CIMD_METADATA_FETCHED;
import static io.gravitee.am.common.audit.EventType.CIMD_METADATA_REJECTED;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CIMDAuditBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefaultEventType() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class).build(objectMapper);
        assertEquals(CIMD_METADATA_FETCHED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldSwitchToRejectedEventType() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class).rejected().build(objectMapper);
        assertEquals(CIMD_METADATA_REJECTED, audit.getType());
    }

    @Test
    void shouldNotFailOnNullOptionalFields() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class)
                .metadataUri(null)
                .softwareId(null)
                .jwksSource(null)
                .documentHash(null)
                .rejectionReason(null)
                .resolvedIp(null)
                .build(objectMapper);
        assertEquals(CIMD_METADATA_FETCHED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldPopulateFullFetchedAuditMessage() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class)
                .metadataUri("https://example.com/.well-known/client")
                .softwareId("sw-1")
                .jwksSource("metadata")
                .documentHash("sha256-abc")
                .fetchDurationMs(42L)
                .cacheHit(false)
                .build(objectMapper);

        String message = audit.getOutcome().getMessage();
        assertNotNull(message);
        assertTrue(message.contains("example.com"));
        assertTrue(message.contains("sw-1"));
        assertTrue(message.contains("metadata"));
        assertTrue(message.contains("sha256-abc"));
        assertTrue(message.contains("42"));
        assertTrue(message.contains("false"));
    }

    @Test
    void shouldPopulateRejectionAuditMessage() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class)
                .rejected()
                .metadataUri("https://evil.example.com/meta")
                .rejectionReason("host not in allowlist")
                .resolvedIp("203.0.113.10")
                .build(objectMapper);

        assertEquals(CIMD_METADATA_REJECTED, audit.getType());
        String message = audit.getOutcome().getMessage();
        assertTrue(message.contains("evil.example.com"));
        assertTrue(message.contains("host not in allowlist"));
        assertTrue(message.contains("203.0.113.10"));
    }

    @Test
    void shouldRecordCacheHitTrue() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class)
                .cacheHit(true)
                .fetchDurationMs(0L)
                .build(objectMapper);
        assertTrue(audit.getOutcome().getMessage().contains("true"));
    }

    @Test
    void shouldReportFailureWhenThrowableSet() {
        var audit = AuditBuilder.builder(CIMDAuditBuilder.class)
                .metadataUri("https://example.com/meta")
                .throwable(new RuntimeException("fetch-failed"))
                .build(objectMapper);
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals("fetch-failed", audit.getOutcome().getMessage());
    }
}
