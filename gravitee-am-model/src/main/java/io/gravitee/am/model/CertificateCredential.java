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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Certificate Credential record for Certificate Based Authentication (CBA)
 *
 * <p>This model is designed for X.509 certificates in PEM format. All certificate-specific
 * fields (thumbprint, SubjectDN, serial number, etc.) are X.509-specific.</p>
 *
 * <p>If support for other certificate formats (e.g., DER, PKCS12) is needed in the future,
 * a {@code certificateFormat} field should be added, and format-specific fields should be
 * stored in the {@code metadata} map or as additional nullable fields.</p>
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateCredential {
    private static final String ISSUER_DN = "issuerDN";

    // Fields shared with other credential types (e.g., WebAuthn credentials)
    private String id;
    private ReferenceType referenceType;
    private String referenceId;
    private String userId;
    private String username;
    private String ipAddress;
    private String userAgent;

    // X.509 certificate-specific fields (PEM format)
    private String certificatePem;
    private String certificateThumbprint;
    private String certificateSubjectDN;
    private String certificateSerialNumber;
    private String certificateIssuerDN;
    @Schema(type = "java.lang.Long")
    private Date certificateExpiresAt; // Certificate expiration date (required)

    // Optional/extensible fields
    private Map<String, String> metadata; // For optional attributes (keyUsage, extendedKeyUsage, subjectAlternativeNames, certificateChain, etc.)

    // Timestamps
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;
    @Schema(type = "java.lang.Long")
    private Date accessedAt;

}

