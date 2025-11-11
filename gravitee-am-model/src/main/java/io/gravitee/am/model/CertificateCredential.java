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
import java.util.Map;

/**
 * Certificate Credential record for Certificate Based Authentication (CBA)
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateCredential {

    // Common fields
    private String id;
    private ReferenceType referenceType;
    private String referenceId;
    private String userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String deviceName;

    // Certificate-specific fields (explicit for querying/indexing)
    private String certificatePem; // PEM public key format (required)
    private String certificateThumbprint; // SHA-256 thumbprint (required)
    private String certificateSubjectDN; // Subject Distinguished Name (required)
    private String certificateSerialNumber; // Certificate serial number (required)
    @Schema(type = "java.lang.Long")
    private Date certificateExpiresAt; // Certificate expiration date (required)

    // Optional/extensible fields
    private Map<String, Object> metadata; // For optional attributes (issuerDN, keyUsage, extendedKeyUsage, subjectAlternativeNames, certificateChain, etc.)

    // Timestamps
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;
    @Schema(type = "java.lang.Long")
    private Date accessedAt;
}

