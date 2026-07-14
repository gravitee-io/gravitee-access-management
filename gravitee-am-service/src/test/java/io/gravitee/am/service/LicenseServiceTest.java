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
package io.gravitee.am.service;

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.service.exception.InvalidLicenseException;
import io.gravitee.am.service.impl.LicenseServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    private static final String ORGANIZATION_ID = "orga#1";
    private static final String LICENSE = Base64.getEncoder().encodeToString("license-content".getBytes());

    @Mock
    private LicenseRepository licenseRepository;

    private LicenseServiceImpl cut;

    @BeforeEach
    void before() {
        cut = new LicenseServiceImpl(licenseRepository);
    }

    @Test
    void createWhenAbsent() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());
        when(licenseRepository.create(any(License.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(license -> license.getReferenceId().equals(ORGANIZATION_ID)
                && license.getReferenceType() == ReferenceType.ORGANIZATION
                && license.getLicense().equals(LICENSE)
                && license.getCreatedAt() != null
                && license.getCreatedAt().equals(license.getUpdatedAt()));
        verify(licenseRepository, never()).update(any(License.class));
    }

    @Test
    void updateWhenPresent() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(Base64.getEncoder().encodeToString("old-license".getBytes()));
        Date createdAt = new Date(0);
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);

        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(existing));
        when(licenseRepository.update(any(License.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(license -> license.getLicense().equals(LICENSE)
                && license.getCreatedAt().equals(createdAt)
                && license.getUpdatedAt().after(createdAt));
        verify(licenseRepository).update(argThat(license -> license.getReferenceId().equals(ORGANIZATION_ID)));
        verify(licenseRepository, never()).create(any(License.class));
    }

    @Test
    void createOrUpdateWithNullLicense() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
    }

    @Test
    void createOrUpdateWithBlankLicense() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "  ").test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
    }

    @Test
    void createOrUpdateWithInvalidBase64License() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "not-base64!!!").test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
    }

    @Test
    void validateAcceptsBase64License() {
        assertDoesNotThrow(() -> cut.validate(LICENSE));
    }

    @Test
    void validateRejectsInvalidLicense() {
        assertThrows(InvalidLicenseException.class, () -> cut.validate(null));
        assertThrows(InvalidLicenseException.class, () -> cut.validate("  "));
        assertThrows(InvalidLicenseException.class, () -> cut.validate("not-base64!!!"));
    }

    @Test
    void delete() {
        when(licenseRepository.delete(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cut.delete(ReferenceType.ORGANIZATION, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        verify(licenseRepository).delete(ORGANIZATION_ID, ReferenceType.ORGANIZATION);
    }
}
