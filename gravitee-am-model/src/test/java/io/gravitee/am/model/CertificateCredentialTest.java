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
package io.gravitee.am.model;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class CertificateCredentialTest {

    @Test
    void shouldSetAndGetAllCommonFields() {
        CertificateCredential credential = new CertificateCredential();
        credential.setId("credential-id");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUserId("user-id");
        credential.setUsername("testuser");
        credential.setIpAddress("192.168.1.1");
        credential.setUserAgent("Mozilla/5.0");
        
        assertThat(credential.getId()).isEqualTo("credential-id");
        assertThat(credential.getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(credential.getReferenceId()).isEqualTo("domain-id");
        assertThat(credential.getUserId()).isEqualTo("user-id");
        assertThat(credential.getUsername()).isEqualTo("testuser");
        assertThat(credential.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(credential.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void shouldSetAndGetAllCertificateSpecificFields() {
        CertificateCredential credential = new CertificateCredential();
        Date expirationDate = new Date();
        
        credential.setCertificatePem("-----BEGIN CERTIFICATE-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END CERTIFICATE-----");
        credential.setCertificateThumbprint("abc123def456...");
        credential.setCertificateSubjectDN("CN=John Doe, O=Example Corp");
        credential.setCertificateSerialNumber("12345678901234567890");
        credential.setCertificateExpiresAt(expirationDate);
        
        assertThat(credential.getCertificatePem()).isEqualTo("-----BEGIN CERTIFICATE-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END CERTIFICATE-----");
        assertThat(credential.getCertificateThumbprint()).isEqualTo("abc123def456...");
        assertThat(credential.getCertificateSubjectDN()).isEqualTo("CN=John Doe, O=Example Corp");
        assertThat(credential.getCertificateSerialNumber()).isEqualTo("12345678901234567890");
        assertThat(credential.getCertificateExpiresAt()).isEqualTo(expirationDate);
    }

    @Test
    void shouldSetAndGetTimestampFields() {
        CertificateCredential credential = new CertificateCredential();
        Date now = new Date();
        
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setAccessedAt(now);
        
        assertThat(credential.getCreatedAt()).isEqualTo(now);
        assertThat(credential.getUpdatedAt()).isEqualTo(now);
        assertThat(credential.getAccessedAt()).isEqualTo(now);
    }

    @Test
    void shouldSetAndGetMetadataField() {
        CertificateCredential credential = new CertificateCredential();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("issuerDN", "CN=CA, O=Example Corp");
        metadata.put("keyUsage", "digitalSignature, keyEncipherment");
        metadata.put("subjectAlternativeNames", "DNS:example.com, DNS:www.example.com");
        
        credential.setMetadata(metadata);
        
        assertThat(credential.getMetadata())
                .isNotNull()
                .containsEntry("issuerDN", "CN=CA, O=Example Corp")
                .containsEntry("keyUsage", "digitalSignature, keyEncipherment")
                .containsEntry("subjectAlternativeNames", "DNS:example.com, DNS:www.example.com");
    }
}

