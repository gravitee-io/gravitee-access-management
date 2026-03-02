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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.certificate.api.ConfigurationCertUtils;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.IdentityProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public record CertificateEntity(
        String id,
        String name,
        String type,
        @Schema(type = "java.lang.Long")
        Date createdAt,
        @Schema(type = "java.lang.Long")
        Date expiresAt,
        boolean system,
        CertificateStatus status,
        List<String> usage,
        List<Application> applications,
        List<IdentityProvider> identityProviders
) {

    public static CertificateEntity singleDetails(Certificate certificate) {
        return new CertificateEntity(certificate.getId(),
                certificate.getName(),
                certificate.getType(),
                certificate.getCreatedAt(),
                certificate.getExpiresAt(),
                certificate.isSystem(),
                null,
                null,
                null,
                null);
    }

    public static CertificateEntity forList(Certificate certificate,
                                            Duration certExpiryWarningThreshold,
                                            boolean isRenewedSystemCert,
                                            List<Application> apps,
                List<IdentityProvider> idps) {
        return new CertificateEntity(certificate.getId(),
                certificate.getName(),
                certificate.getType(),
                certificate.getCreatedAt(),
                certificate.getExpiresAt(),
                certificate.isSystem(),
                determineStatus(certificate.getExpiresAt(), certExpiryWarningThreshold, isRenewedSystemCert),
                extractUsages(certificate.getConfiguration()),
                apps,
                idps);
    }

    static CertificateStatus determineStatus(Date expiresAt, Duration certExpiryWarningThreshold, boolean isRenewedSystemCert) {
        var now = Instant.now();
        if (isRenewedSystemCert) {
            return CertificateStatus.RENEWED;
        } else if (expiresAt != null && expiresAt.getTime() <= now.toEpochMilli()) {
            return CertificateStatus.EXPIRED;
        } else if (expiresAt != null && expiresAt.getTime() < now.plus(certExpiryWarningThreshold).toEpochMilli()) {
            return CertificateStatus.WILL_EXPIRE;
        } else {
            return CertificateStatus.VALID;
        }
    }

    static List<String> extractUsages(String certificateConfig) {
        return ConfigurationCertUtils.extractUsageFromCertConfiguration(certificateConfig);
    }
}
