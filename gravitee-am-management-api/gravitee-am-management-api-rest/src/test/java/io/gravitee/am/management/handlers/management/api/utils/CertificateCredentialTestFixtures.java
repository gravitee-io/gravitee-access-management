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
package io.gravitee.am.management.handlers.management.api.utils;

import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;

import java.util.Date;
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
     * Build a minimal CertificateCredential for testing with essential fields only.
     * Uses random UUID to ensure uniqueness across tests.
     * For API tests, we only need basic fields for mocking service responses.
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
        credential.setCertificateIssuerDN("CN=Issuer" + randomStr);
        credential.setCertificateExpiresAt(new Date(System.currentTimeMillis() + 86400000)); // Tomorrow
        return credential;
    }
}

