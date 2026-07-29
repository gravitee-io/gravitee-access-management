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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.service.exception.InvalidLicenseException;
import io.gravitee.am.service.impl.LicenseServiceImpl;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.MalformedLicenseException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    private static final String OLD_LICENSE = Base64.getEncoder().encodeToString("old-license".getBytes());

    private static final String TIER = "galaxy";
    private static final String OLD_TIER = "planet";
    private static final Date EXPIRES_AT = new Date(1893456000000L);

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @Mock
    private LicenseFactory licenseFactory;

    private LicenseServiceImpl cut;

    @BeforeEach
    void before() throws Exception {
        cut = new LicenseServiceImpl(licenseRepository, eventService, auditService, licenseFactory);
        lenient().when(eventService.create(any(Event.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        lenient().when(licenseFactory.create(anyString(), anyString(), nullable(String.class)))
                .thenAnswer(invocation -> nodeLicense(tierOf(invocation.getArgument(2))));
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
        verify(eventService).create(argThat(event -> event.getType() == Type.LICENSE
                && event.getPayload().getReferenceType() == ReferenceType.ORGANIZATION
                && event.getPayload().getReferenceId().equals(ORGANIZATION_ID)
                && event.getPayload().getAction() == Action.CREATE));

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_CREATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ReferenceType.ORGANIZATION, audit.getReferenceType());
        assertEquals(ORGANIZATION_ID, audit.getReferenceId());
        assertEquals(EntityType.LICENSE, audit.getTarget().getType());
        assertEquals(ORGANIZATION_ID, audit.getTarget().getId());
        // an organization has at most one license, so there is no descriptor to display
        assertNull(audit.getTarget().getDisplayName());
        assertTrue(audit.getOutcome().getMessage().contains(TIER), "the granted tier should be recorded");
        assertNoRawLicense(audit);
    }

    @Test
    void updateWhenPresent() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(OLD_LICENSE);
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
        verify(eventService).create(argThat(event -> event.getType() == Type.LICENSE
                && event.getPayload().getAction() == Action.UPDATE));

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_UPDATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ORGANIZATION_ID, audit.getReferenceId());
        // the diff is what an operator reads to see which entitlements the organization gained or lost
        String message = audit.getOutcome().getMessage();
        assertTrue(message.contains("/tier"), "the tier transition should appear in the diff: " + message);
        assertTrue(message.contains(TIER), "the new tier should appear in the diff: " + message);
        assertNoRawLicense(audit);
    }

    @Test
    void skipUpdateWhenLicenseUnchanged() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(LICENSE);

        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(existing));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(existing);
        verify(licenseRepository, never()).update(any(License.class));
        verify(licenseRepository, never()).create(any(License.class));
        verifyNoInteractions(eventService);
        // nothing changed, so nothing to audit
        verifyNoInteractions(auditService);
    }

    @Test
    void createOrUpdateWithNullLicense() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, null).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
        verifyNoInteractions(eventService);

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_UPDATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
    }

    @Test
    void createOrUpdateWithBlankLicense() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "  ").test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
        verifyNoInteractions(eventService);
        assertEquals(Status.FAILURE, capturedAudit().getOutcome().getStatus());
    }

    @Test
    void createOrUpdateWithInvalidBase64License() {
        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "not-base64!!!").test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidLicenseException.class);
        verifyNoInteractions(licenseRepository);
        verifyNoInteractions(eventService);
        assertEquals(Status.FAILURE, capturedAudit().getOutcome().getStatus());
    }

    @Test
    void createReportsCreationFailureAuditWhenRepositoryFails() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());
        when(licenseRepository.create(any(License.class))).thenReturn(Single.error(new TechnicalException()));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_CREATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(ORGANIZATION_ID, audit.getReferenceId());
    }

    @Test
    void updateReportsUpdateFailureAuditWhenRepositoryFails() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(OLD_LICENSE);

        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(existing));
        when(licenseRepository.update(any(License.class))).thenReturn(Single.error(new TechnicalException()));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_UPDATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(ORGANIZATION_ID, audit.getReferenceId());
    }

    @Test
    void createOrUpdateReportsFailureAuditWhenLookupFails() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.error(new TechnicalException()));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);

        // no write was attempted, so the intended operation is unknowable
        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_UPDATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
    }

    @Test
    void auditIsStillReportedWhenLicenseCannotBeDecoded() throws Exception {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());
        when(licenseRepository.create(any(License.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(licenseFactory.create(anyString(), anyString(), nullable(String.class)))
                .thenThrow(new MalformedLicenseException("corrupted"));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.ORGANIZATION, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();

        // the change is recorded even when its entitlements cannot be read, just without the detail
        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_CREATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void noAuditForNonOrganizationReference() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.PLATFORM)).thenReturn(Maybe.empty());
        when(licenseRepository.create(any(License.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<License> obs = cut.createOrUpdate(ReferenceType.PLATFORM, ORGANIZATION_ID, LICENSE).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        // the audit event types are organization-scoped
        verifyNoInteractions(auditService);
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
    void deleteWhenPresent() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(OLD_LICENSE);

        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(existing));
        when(licenseRepository.delete(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Completable.complete());

        TestObserver<Void> obs = cut.delete(ReferenceType.ORGANIZATION, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        verify(licenseRepository).delete(ORGANIZATION_ID, ReferenceType.ORGANIZATION);
        verify(eventService).create(argThat(event -> event.getType() == Type.LICENSE
                && event.getPayload().getReferenceId().equals(ORGANIZATION_ID)
                && event.getPayload().getAction() == Action.DELETE));

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_DELETED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals(ORGANIZATION_ID, audit.getReferenceId());
        assertEquals(EntityType.LICENSE, audit.getTarget().getType());
        // the removed entitlements are what makes a silent downgrade traceable
        assertTrue(audit.getOutcome().getMessage().contains(OLD_TIER),
                "the removed tier should be recorded: " + audit.getOutcome().getMessage());
        assertNoRawLicense(audit);
    }

    @Test
    void deleteWhenAbsent() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());

        TestObserver<Void> obs = cut.delete(ReferenceType.ORGANIZATION, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        verify(licenseRepository, never()).delete(anyString(), any(ReferenceType.class));
        verifyNoInteractions(eventService);
        // nothing was removed, so nothing to audit
        verifyNoInteractions(auditService);
    }

    @Test
    void deleteReportsFailureAuditWhenRepositoryFails() {
        License existing = new License();
        existing.setReferenceId(ORGANIZATION_ID);
        existing.setReferenceType(ReferenceType.ORGANIZATION);
        existing.setLicense(OLD_LICENSE);

        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(existing));
        when(licenseRepository.delete(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Completable.error(new TechnicalException()));

        TestObserver<Void> obs = cut.delete(ReferenceType.ORGANIZATION, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(TechnicalException.class);

        Audit audit = capturedAudit();
        assertEquals(EventType.ORGANIZATION_LICENSE_DELETED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
    }

    @Test
    void findAll() {
        when(licenseRepository.findAll()).thenReturn(Flowable.empty());

        cut.findAll().test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        verify(licenseRepository).findAll();
    }

    @Test
    void findByReference() {
        when(licenseRepository.findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION)).thenReturn(Maybe.empty());

        cut.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID).test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        verify(licenseRepository).findById(ORGANIZATION_ID, ReferenceType.ORGANIZATION);
    }

    private Audit capturedAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(new ObjectMapper());
    }

    /**
     * The persisted license is a signed secret: it must never reach the audit record.
     */
    private static void assertNoRawLicense(Audit audit) {
        String message = audit.getOutcome().getMessage();
        assertFalse(message.contains(LICENSE), "the raw license leaked into the audit: " + message);
        assertFalse(message.contains(OLD_LICENSE), "the raw license leaked into the audit: " + message);
    }

    private static String tierOf(String rawLicense) {
        return OLD_LICENSE.equals(rawLicense) ? OLD_TIER : TIER;
    }

    private static io.gravitee.node.api.license.License nodeLicense(String tier) {
        var license = mock(io.gravitee.node.api.license.License.class);
        lenient().when(license.getTier()).thenReturn(tier);
        lenient().when(license.getPacks()).thenReturn(Set.of("pack-" + tier));
        lenient().when(license.getFeatures()).thenReturn(Set.of("feature-" + tier));
        lenient().when(license.getExpirationDate()).thenReturn(EXPIRES_AT);
        lenient().when(license.isExpired()).thenReturn(false);
        lenient().when(license.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION.name());
        return license;
    }
}
