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

import io.gravitee.am.model.Certificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
class CertificateTimeComparatorTest {

    @ParameterizedTest
    @MethodSource
    @DisplayName("Sort Certificates based on expiration date order by desc")
    void shouldSortCertificate_ByExpiration_orderBy_DESC(List<Certificate> certificates, Certificate expectedCertificate) {
        final Optional<Certificate> first = certificates.stream().sorted(new CertificateTimeComparator()).findFirst();
        assertTrue(first.isPresent());
        assertEquals(expectedCertificate.getId(), first.get().getId());
    }

    static Stream<Arguments> shouldSortCertificate_ByExpiration_orderBy_DESC() {
        final var now = Instant.now();
        var cert1 = new Certificate();
        cert1.setId("cert1");
        cert1.setCreatedAt(new Date(now.toEpochMilli()));

        var cert2 = new Certificate();
        cert2.setId("cert2");
        cert2.setCreatedAt(new Date(now.plusSeconds(10).toEpochMilli()));
        cert2.setExpiresAt(new Date(now.plusSeconds(15).toEpochMilli()));
        var cert2bis = new Certificate();
        cert2bis.setId("cert2bis");
        cert2bis.setCreatedAt(new Date(now.plusSeconds(10).toEpochMilli()));
        cert2bis.setExpiresAt(new Date(now.plusSeconds(20).toEpochMilli()));

        var cert3 = new Certificate();
        cert3.setId("cert3");
        cert3.setCreatedAt(new Date(now.minusSeconds(10).toEpochMilli()));

        return Stream.of(
                Arguments.arguments(List.of(cert2, cert1), cert2),
                Arguments.arguments(List.of(cert1, cert2), cert2),
                Arguments.arguments(List.of(cert1, cert3, cert2), cert2),
                Arguments.arguments(List.of(cert1, cert3), cert1),
                Arguments.arguments(List.of(cert2, cert2bis), cert2bis)
                );
    }
}
