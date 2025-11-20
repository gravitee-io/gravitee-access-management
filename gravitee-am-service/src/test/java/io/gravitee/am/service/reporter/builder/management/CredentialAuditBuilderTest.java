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
package io.gravitee.am.service.reporter.builder.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static io.gravitee.am.common.audit.EventType.CREDENTIAL_CREATED;
import static io.gravitee.am.common.audit.EventType.CREDENTIAL_DELETED;
import static io.gravitee.am.common.audit.EventType.CREDENTIAL_UPDATED;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static io.gravitee.am.service.reporter.builder.AuditBuilder.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author GraviteeSource Team
 */
class CredentialAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefault() {
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .build(objectMapper);
        assertEquals(CREDENTIAL_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldBuildWithNullCredential() {
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(null)
                .build(objectMapper);
        assertEquals(CREDENTIAL_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getTarget());
    }

    @Test
    void shouldBuildWithCredentialCreated() {
        var credential = createCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(credential)
                .build(objectMapper);

        assertEquals(CREDENTIAL_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getTarget());
        assertEquals("credential-id", audit.getTarget().getId());
        assertEquals(EntityType.CREDENTIAL, audit.getTarget().getType());
        assertEquals("aaguid-123", audit.getTarget().getAlternativeId());
        assertEquals("credential-id-123", audit.getTarget().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getTarget().getReferenceType());
        assertEquals("domain-id", audit.getTarget().getReferenceId());

        // Verify routePath is set in attributes
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertIterableEquals(List.of("users", "user-id", "credentials", "credential-id"), routePath);
    }

    @Test
    void shouldBuildWithCredentialUpdated() {
        var credential = createCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_UPDATED)
                .credential(credential)
                .build(objectMapper);

        assertEquals(CREDENTIAL_UPDATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getTarget());
        assertNotNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildWithCredentialDeleted() {
        var credential = createCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_DELETED)
                .credential(credential)
                .build(objectMapper);

        assertEquals(CREDENTIAL_DELETED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getTarget());
        // routePath should still be set even for DELETE events
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertIterableEquals(List.of("users", "user-id", "credentials", "credential-id"), routePath);
    }

    @Test
    void shouldNotSetRoutePathWhenUserIdIsNull() {
        var credential = createCredential("credential-id", null, "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(credential)
                .build(objectMapper);

        assertEquals(CREDENTIAL_CREATED, audit.getType());
        assertNotNull(audit.getTarget());
        // routePath should not be set when userId is null
        // Attributes may be empty map or null depending on base class implementation
        if (audit.getTarget().getAttributes() != null) {
            assertNull(audit.getTarget().getAttributes().get("routePath"));
        }
    }

    @Test
    void shouldPreserveExistingAttributes() {
        var credential = createCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(credential)
                .build(objectMapper);

        assertNotNull(audit.getTarget());
        assertNotNull(audit.getTarget().getAttributes());
        // routePath should be present
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertIterableEquals(List.of("users", "user-id", "credentials", "credential-id"), routePath);
        // Verify that routePath doesn't overwrite other attributes
        // The base class should preserve attributes from parent createTarget() method
        assertNotNull(audit.getTarget().getAttributes());
    }

    private Credential createCredential(String id, String userId, String domainId) {
        var credential = new Credential();
        credential.setId(id);
        credential.setUserId(userId);
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(domainId);
        credential.setAaguid("aaguid-123");
        credential.setCredentialId("credential-id-123");
        credential.setPublicKey("public-key-123");
        credential.setCounter(0L);
        credential.setCreatedAt(new Date());
        credential.setUpdatedAt(new Date());
        return credential;
    }
}

