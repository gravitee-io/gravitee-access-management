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
package io.gravitee.am.repository.jdbc.management.api.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author GraviteeSource Team
 */
@Table("cert_credentials")
@Getter
@Setter
public class JdbcCertificateCredential {
    @Id
    private String id;
    @Column("reference_type")
    private String referenceType;
    @Column("reference_id")
    private String referenceId;
    @Column("user_id")
    private String userId;
    private String username;
    @Column("ip_address")
    private String ipAddress;
    @Column("user_agent")
    private String userAgent;
    @Column("device_name")
    private String deviceName;

    // Certificate-specific fields
    @Column("certificate_pem")
    private String certificatePem;
    @Column("certificate_thumbprint")
    private String certificateThumbprint;
    @Column("certificate_subject_dn")
    private String certificateSubjectDN;
    @Column("certificate_serial_number")
    private String certificateSerialNumber;
    @Column("certificate_expires_at")
    private LocalDateTime certificateExpiresAt;

    // Optional/extensible fields (JSON)
    private String metadata;

    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    @Column("accessed_at")
    private LocalDateTime accessedAt;
}

