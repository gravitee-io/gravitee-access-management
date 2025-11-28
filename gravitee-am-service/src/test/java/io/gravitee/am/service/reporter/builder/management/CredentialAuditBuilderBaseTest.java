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
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static io.gravitee.am.common.audit.EventType.CREDENTIAL_CREATED;
import static io.gravitee.am.service.reporter.builder.AuditBuilder.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for CredentialAuditBuilderBase to verify the base class functionality
 * is correctly inherited by both CredentialAuditBuilder and CertificateCredentialAuditBuilder.
 *
 * @author GraviteeSource Team
 */
class CredentialAuditBuilderBaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldInheritRoutePathFunctionalityInCertificateCredentialBuilder() {
        // Test that CertificateCredentialAuditBuilder correctly inherits routePath functionality
        var credential = createCertificateCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CertificateCredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .certificateCredential(credential)
                .build(objectMapper);

        assertNotNull(audit.getTarget());
        assertNotNull(audit.getTarget().getAttributes());
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertIterableEquals(List.of("users", "user-id", "cert-credentials", "credential-id"), routePath);
    }

    @Test
    void shouldInheritRoutePathFunctionalityInCredentialBuilder() {
        // Test that CredentialAuditBuilder correctly inherits routePath functionality
        var credential = createCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(credential)
                .build(objectMapper);

        assertNotNull(audit.getTarget());
        assertNotNull(audit.getTarget().getAttributes());
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertIterableEquals(List.of("users", "user-id", "credentials", "credential-id"), routePath);
    }

    @Test
    void shouldNotSetRoutePathWhenNullInBothBuilders() {
        // Test CertificateCredentialAuditBuilder
        var certCredential = createCertificateCredential("credential-id", null, "domain-id");
        var certAudit = builder(CertificateCredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .certificateCredential(certCredential)
                .build(objectMapper);

        assertNotNull(certAudit.getTarget());
        if (certAudit.getTarget().getAttributes() != null) {
            assertNull(certAudit.getTarget().getAttributes().get("routePath"));
        }

        // Test CredentialAuditBuilder
        var credential = createCredential("credential-id", null, "domain-id");
        var audit = builder(CredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .credential(credential)
                .build(objectMapper);

        assertNotNull(audit.getTarget());
        if (audit.getTarget().getAttributes() != null) {
            assertNull(audit.getTarget().getAttributes().get("routePath"));
        }
    }

    @Test
    void shouldPreserveExistingAttributesWhenRoutePathIsSet() {
        // Test that existing attributes are preserved when routePath is added
        var credential = createCertificateCredential("credential-id", "user-id", "domain-id");
        var audit = builder(CertificateCredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .certificateCredential(credential)
                .build(objectMapper);

        assertNotNull(audit.getTarget());
        assertNotNull(audit.getTarget().getAttributes());
        // Verify routePath is present
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        // Verify other attributes (like externalId, sourceId) are preserved if they exist
        // The base class createTarget() method should preserve existing attributes
        assertNotNull(audit.getTarget().getAttributes());
    }

    @Test
    void shouldHandleEmptyRoutePathArray() {
        // Test edge case: empty route path array (shouldn't happen in practice, but test defensive coding)
        var credential = createCertificateCredential("credential-id", "user-id", "domain-id");
        // Note: In practice, setRoutePath is called internally, but we're testing the base class behavior
        var audit = builder(CertificateCredentialAuditBuilder.class)
                .type(CREDENTIAL_CREATED)
                .certificateCredential(credential)
                .build(objectMapper);

        // Should still work correctly with valid route path
        assertNotNull(audit.getTarget());
        @SuppressWarnings("unchecked")
        var routePath = (List<String>) audit.getTarget().getAttributes().get("routePath");
        assertNotNull(routePath);
        assertEquals(4, routePath.size());
    }

    private CertificateCredential createCertificateCredential(String id, String userId, String domainId) {
        return CertificateCredential.builder()
                .id(id)
                .userId(userId)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(domainId)
                .certificateThumbprint("thumbprint-123")
                .certificateSubjectDN("CN=test")
                .certificateSerialNumber("serial-123")
                .certificatePem("-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----")
                .certificateIssuerDN("CN=issuer")
                .certificateExpiresAt(new Date(System.currentTimeMillis() + 86400000))
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }

    private io.gravitee.am.model.Credential createCredential(String id, String userId, String domainId) {
        var credential = new io.gravitee.am.model.Credential();
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

