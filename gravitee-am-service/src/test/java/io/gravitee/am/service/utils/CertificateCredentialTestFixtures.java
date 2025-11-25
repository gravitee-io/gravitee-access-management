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
package io.gravitee.am.service.utils;

import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test fixtures for CertificateCredential tests.
 *
 * @author GraviteeSource Team
 */
public final class CertificateCredentialTestFixtures {

    private CertificateCredentialTestFixtures() {
        // Utility class - prevent instantiation
    }

    /**
     * Build a CertificateCredential for testing with all fields set.
     * Uses random UUID to ensure uniqueness across tests.
     *
     * @param domain the domain for the credential
     * @param userId the user ID for the credential
     * @param certificatePem the PEM-encoded certificate
     * @return a CertificateCredential with all fields set
     */
    public static CertificateCredential buildCertificateCredential(Domain domain, String userId, String certificatePem) {
        String randomStr = UUID.randomUUID().toString();
        Date expirationDate = new Date(System.currentTimeMillis() + 86400000); // Tomorrow

        Map<String, String> metadata = new HashMap<>();
        metadata.put("issuerDN", "CN=CA, O=Example Corp");
        metadata.put("keyUsage", "digitalSignature, keyEncipherment");

        CertificateCredential credential = new CertificateCredential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(domain.getId());
        credential.setUserId(userId);
        credential.setUsername("test-user-" + randomStr);
        credential.setCertificatePem(certificatePem);
        credential.setCertificateThumbprint("thumbprint-" + randomStr);
        credential.setCertificateSubjectDN("CN=Test User " + randomStr);
        credential.setCertificateSerialNumber("serial-" + randomStr);
        credential.setCertificateExpiresAt(expirationDate);
        credential.setMetadata(metadata);
        credential.setAccessedAt(new Date());
        credential.setUserAgent("test-user-agent");
        credential.setIpAddress("127.0.0.1");
        return credential;
    }

    /**
     * Build a minimal CertificateCredential for testing with essential fields only.
     * Uses random UUID to ensure uniqueness across tests.
     *
     * @param domainId the domain ID for the credential
     * @param userId the user ID for the credential
     * @return a CertificateCredential with essential fields set
     */
    public static CertificateCredential buildMinimalCertificateCredential(String domainId, String userId) {
        String randomStr = UUID.randomUUID().toString();
        CertificateCredential credential = new CertificateCredential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(domainId);
        credential.setUserId(userId);
        credential.setCertificateThumbprint("thumbprint-" + randomStr);
        credential.setCertificateSubjectDN("CN=Test Certificate " + randomStr);
        credential.setCertificateSerialNumber("serial-" + randomStr);
        credential.setCertificateExpiresAt(new Date(System.currentTimeMillis() + 86400000)); // Tomorrow
        return credential;
    }
}

