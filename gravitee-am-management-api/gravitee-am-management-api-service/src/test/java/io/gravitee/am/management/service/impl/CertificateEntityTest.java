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

import io.gravitee.am.model.Certificate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.management.service.impl.CertificateStatus.EXPIRED;
import static io.gravitee.am.management.service.impl.CertificateStatus.RENEWED;
import static io.gravitee.am.management.service.impl.CertificateStatus.VALID;
import static io.gravitee.am.management.service.impl.CertificateStatus.WILL_EXPIRE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CertificateEntityTest {
    private final static Duration expirationWarningThreshold = Duration.ofDays(5);


    public static Arguments[] params() {
        var now = Instant.now();
        return new Arguments[]{
                arguments(false, now.plus(expirationWarningThreshold.multipliedBy(2)), VALID),
                arguments(false, now.plus(expirationWarningThreshold.dividedBy(2)), WILL_EXPIRE),
                arguments(false, now.minusSeconds(3600), EXPIRED),
                // renewed certs should be RENEWED regardless of actual expiration date
                arguments(true, now.plus(expirationWarningThreshold.multipliedBy(2)), RENEWED),
                arguments(true, now.plus(expirationWarningThreshold.dividedBy(2)), RENEWED),
                arguments(true, now.minusSeconds(3600), RENEWED),
                arguments(false, null, VALID)
        };
    }

    @ParameterizedTest
    @MethodSource("params")
    void hasCorrectStatus(boolean isRenewedSystemCert, Instant certExpiresAt, CertificateStatus expectedStatus) {
        var cert = aCertificateExpiringAt(certExpiresAt);
        var entity = CertificateEntity.forList(cert, expirationWarningThreshold, isRenewedSystemCert, List.of(), List.of());

        assertThat(entity.status()).isEqualTo(expectedStatus);
    }


    private Certificate aCertificateExpiringAt(Instant certExpiresAt) {
        var cert = new Certificate();
        cert.setExpiresAt(certExpiresAt == null ? null : Date.from(certExpiresAt));
        return cert;
    }


}