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
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.management.CertificateAuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.gravitee.am.common.audit.EventType.CERTIFICATE_CREATED;
import static io.gravitee.am.common.audit.EventType.CLIENT_AUTHENTICATION;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertificateAuditBuilderTest {
    public static final String MD_VALUE = "a value to filer";
    public static final String MD_VALUE_2 = "an updated value to filer";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldResetMetadata_onCreate() {
        final var cert = new Certificate();
        cert.setMetadata(Map.of("key", MD_VALUE));
        cert.setId(UUID.randomUUID().toString());
        cert.setConfiguration(UUID.randomUUID().toString());
        cert.setName("MyCertificate");
        cert.setDomain(UUID.randomUUID().toString());
        cert.setType(UUID.randomUUID().toString());

        var audit = CertificateAuditBuilder.builder(CertificateAuditBuilder.class)
                .type(CERTIFICATE_CREATED)
                .certificate(cert)
                .throwable(null)
                .build(objectMapper);

        assertEquals(CERTIFICATE_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getOutcome().getMessage());
        assertFalse(audit.getOutcome().getMessage().contains(MD_VALUE));
    }

    @Test
    void shouldResetMetadata_onUpdate() {
        final var existingCert = new Certificate();
        existingCert.setMetadata(Map.of("key", MD_VALUE));
        existingCert.setId(UUID.randomUUID().toString());
        existingCert.setConfiguration(UUID.randomUUID().toString());
        existingCert.setName("MyCertificate");
        existingCert.setDomain(UUID.randomUUID().toString());
        existingCert.setType(UUID.randomUUID().toString());
        final var updateCert = new Certificate(existingCert);
        updateCert.setMetadata(Map.of("key", MD_VALUE_2));
        updateCert.setConfiguration(UUID.randomUUID().toString());
        updateCert.setName("MyCertificate2");

        var audit = CertificateAuditBuilder.builder(CertificateAuditBuilder.class)
                .type(CERTIFICATE_CREATED)
                .certificate(updateCert)
                .oldValue(existingCert)
                .throwable(null)
                .build(objectMapper);

        assertEquals(CERTIFICATE_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getOutcome().getMessage());
        assertFalse(audit.getOutcome().getMessage().contains(MD_VALUE));
        assertFalse(audit.getOutcome().getMessage().contains(MD_VALUE_2));
    }

}
