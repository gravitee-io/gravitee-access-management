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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.dataplane.api.repository.CertificateCredentialRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.exception.CertificateExpiredException;
import io.gravitee.am.service.exception.CertificateLimitExceededException;
import io.gravitee.am.service.exception.DuplicateCertificateException;
import io.gravitee.am.service.impl.CertificateCredentialServiceImpl;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.utils.CertificateCredentialTestFixtures;
import io.gravitee.am.service.utils.CertificateTestUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateCredentialServiceTest {

    // Test constants
    private static final String DOMAIN_ID = "my-domain";
    private static final String USER_ID = "user-123";
    private static final int MAX_CERTIFICATES_PER_USER = 20;
    private static final int CERTIFICATE_LIMIT = 20;
    
    // Test certificates (generated at runtime)
    private static final String VALID_PEM_CERT = CertificateTestUtils.generateValidCertificatePEM();
    private static final String EXPIRED_PEM_CERT = CertificateTestUtils.generateExpiredCertificatePEM();
    
    // Test domain (initialized in @Before)
    private static final Domain DOMAIN = new Domain(DOMAIN_ID);

    private static final io.gravitee.am.model.User USER = new io.gravitee.am.model.User();

    @InjectMocks
    private CertificateCredentialService certificateCredentialService = new CertificateCredentialServiceImpl();

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private CertificateCredentialRepository certificateCredentialRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private User principal;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        DOMAIN.setId(DOMAIN_ID);
        DOMAIN.setReferenceType(ReferenceType.DOMAIN);
        DOMAIN.setReferenceId(DOMAIN_ID);
        USER.setId(USER_ID);
        USER.setUsername(USER_ID);
        ReflectionTestUtils.setField(certificateCredentialService, "maxCertificatesPerUser", MAX_CERTIFICATES_PER_USER);
        ReflectionTestUtils.setField(certificateCredentialService, "auditService", auditService);
        Mockito.when(dataPlaneRegistry.getCertificateCredentialRepository(Mockito.argThat(d -> d.getId().equals(DOMAIN_ID))))
                .thenReturn(certificateCredentialRepository);
        Mockito.when(dataPlaneRegistry.getUserRepository(Mockito.argThat(d -> d.getId().equals(DOMAIN_ID))))
                .thenReturn(userRepository);
        Mockito.when(userRepository.findById(USER_ID)).thenReturn(Maybe.just(USER));
    }

    @Test
    public void shouldEnrollCertificate() {
        setupSuccessfulEnrollmentMocks();

        CertificateCredential createdCredential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        createdCredential.setId("credential-id");
        when(certificateCredentialRepository.create(any(CertificateCredential.class)))
                .thenReturn(Single.just(createdCredential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .enrollCertificate(DOMAIN, USER_ID, VALID_PEM_CERT, principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(credential -> {
            assertNotNull(credential.getId());
            assertNotNull(credential.getCertificateThumbprint());
            assertNotNull(credential.getCertificateSubjectDN());
            return true;
        });

        verify(certificateCredentialRepository, times(1)).create(any(CertificateCredential.class));
        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(objectMapper);
            return EventType.CREDENTIAL_CREATED.equals(audit.getType()) &&
                   ReferenceType.DOMAIN.equals(audit.getReferenceType()) &&
                   DOMAIN_ID.equals(audit.getReferenceId()) &&
                   Status.SUCCESS.equals(audit.getOutcome().getStatus());
        }));
    }


    @Test
    public void shouldEnrollExpiredCertificate_throwException() {
        // No mocks needed - service fails early on expiration check before repository calls

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .enrollCertificate(DOMAIN, USER_ID, EXPIRED_PEM_CERT, principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(CertificateExpiredException.class);
        testObserver.assertNotComplete();

        // Verify repository was not called (enrollment should fail before any repository calls)
        verify(certificateCredentialRepository, never()).findByThumbprint(any(), anyString(), anyString());
        verify(certificateCredentialRepository, never()).findByUserId(any(), anyString(), anyString());
        verify(certificateCredentialRepository, never()).create(any(CertificateCredential.class));
        // Verify audit logging was called even on error
        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(objectMapper);
            return EventType.CREDENTIAL_CREATED.equals(audit.getType()) &&
                   ReferenceType.DOMAIN.equals(audit.getReferenceType()) &&
                   DOMAIN_ID.equals(audit.getReferenceId()) &&
                   Status.FAILURE.equals(audit.getOutcome().getStatus());
        }));
    }

    @Test
    public void shouldEnrollDuplicateCertificate_throwException() {
        CertificateCredential existingCredential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        existingCredential.setId("existing-id");
        when(certificateCredentialRepository.findByThumbprint(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), anyString()))
                .thenReturn(Maybe.just(existingCredential));
        when(certificateCredentialRepository.findByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID)))
                .thenReturn(Flowable.empty());

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .enrollCertificate(DOMAIN, USER_ID, VALID_PEM_CERT, principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(DuplicateCertificateException.class);
        testObserver.assertNotComplete();
        // Verify audit logging was called even on error
        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(objectMapper);
            return EventType.CREDENTIAL_CREATED.equals(audit.getType()) &&
                   ReferenceType.DOMAIN.equals(audit.getReferenceType()) &&
                   DOMAIN_ID.equals(audit.getReferenceId()) &&
                   Status.FAILURE.equals(audit.getOutcome().getStatus());
        }));
    }

    @Test
    public void shouldEnrollCertificateLimitExceeded_throwException() {
        // Mock repository to return existing credentials (limit reached)
        List<CertificateCredential> existingCredentials = createCredentialsList(CERTIFICATE_LIMIT);

        when(certificateCredentialRepository.findByThumbprint(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), anyString()))
                .thenReturn(Maybe.empty());
        when(certificateCredentialRepository.findByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID)))
                .thenReturn(Flowable.fromIterable(existingCredentials));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .enrollCertificate(DOMAIN, USER_ID, VALID_PEM_CERT, principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(CertificateLimitExceededException.class);
        testObserver.assertNotComplete();
        // Verify audit logging was called even on error
        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(objectMapper);
            return EventType.CREDENTIAL_CREATED.equals(audit.getType()) &&
                   ReferenceType.DOMAIN.equals(audit.getReferenceType()) &&
                   DOMAIN_ID.equals(audit.getReferenceId()) &&
                   Status.FAILURE.equals(audit.getOutcome().getStatus());
        }));
    }

    @Test
    public void shouldFindByUserId() {
        List<CertificateCredential> credentials = createCredentialsList(2);
        credentials.get(0).setId("cred-1");
        credentials.get(1).setId("cred-2");

        when(certificateCredentialRepository.findByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID)))
                .thenReturn(Flowable.fromIterable(credentials));

        TestSubscriber<CertificateCredential> testSubscriber = certificateCredentialService
                .findByUserId(DOMAIN, USER_ID)
                .test();

        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
    }

    @Test
    public void shouldFindById() {
        CertificateCredential credential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        credential.setId("credential-id");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(DOMAIN_ID);

        when(certificateCredentialRepository.findById("credential-id"))
                .thenReturn(Maybe.just(credential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .findById(DOMAIN, "credential-id")
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(cred -> cred.getId().equals("credential-id"));
    }

    @Test
    public void shouldFindByThumbprint() {
        CertificateCredential credential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        credential.setId("credential-id");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(DOMAIN_ID);

        when(certificateCredentialRepository.findByThumbprint(ReferenceType.DOMAIN, DOMAIN_ID, "thumbprint"))
                .thenReturn(Maybe.just(credential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .findByThumbprint(DOMAIN, "thumbprint")
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(cred -> cred.getId().equals("credential-id"));
    }

    @Test
    public void shouldFindByPrimaryMetadata() {
        CertificateCredential credential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        credential.setId("credential-id");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(DOMAIN_ID);

        when(certificateCredentialRepository.findBySubjectAndIssuerAndSerialNumber(
                ReferenceType.DOMAIN,
                DOMAIN_ID,
                credential.getCertificateSubjectDN(),
                credential.getCertificateIssuerDN(),
                credential.getCertificateSerialNumber()))
                .thenReturn(Maybe.just(credential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .findByPrimaryMetadata(DOMAIN, credential.getCertificateSubjectDN(),
                        credential.getCertificateIssuerDN(),
                        credential.getCertificateSerialNumber())
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(cred -> cred.getId().equals("credential-id"));
    }


    @Test
    public void shouldFindById_wrongDomain() {
        // Test domain tenancy check - credential belongs to different domain
        CertificateCredential credential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        credential.setId("credential-id");
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("different-domain-id"); // Different domain

        when(certificateCredentialRepository.findById("credential-id"))
                .thenReturn(Maybe.just(credential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .findById(DOMAIN, "credential-id")
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues(); // Should return empty due to domain tenancy check
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldFindById_notFound() {
        when(certificateCredentialRepository.findById("credential-id"))
                .thenReturn(Maybe.empty());

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .findById(DOMAIN, "credential-id")
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldDelete() {
        when(certificateCredentialRepository.delete("credential-id"))
                .thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        TestObserver<Void> testObserver = certificateCredentialService
                .delete(DOMAIN, "credential-id")
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateCredentialRepository, times(1)).delete("credential-id");
    }

    @Test
    public void shouldDeleteByDomainAndUserAndId() {
        CertificateCredential credential = CertificateCredentialTestFixtures.buildCertificateCredential(
                DOMAIN, USER_ID, VALID_PEM_CERT);
        credential.setId("credential-id");

        when(certificateCredentialRepository.deleteByDomainAndUserAndId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID), eq("credential-id")))
                .thenReturn(Maybe.just(credential));

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .deleteByDomainAndUserAndId(DOMAIN, USER_ID, "credential-id", principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(cred -> cred.getId().equals("credential-id"));
        testObserver.assertValue(cred -> cred.getUserId().equals(USER_ID));

        verify(certificateCredentialRepository, times(1)).deleteByDomainAndUserAndId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID), eq("credential-id"));
        verify(auditService, times(1)).report(argThat(builder -> {
            Audit audit = builder.build(objectMapper);
            return EventType.CREDENTIAL_DELETED.equals(audit.getType()) &&
                   ReferenceType.DOMAIN.equals(audit.getReferenceType()) &&
                   DOMAIN_ID.equals(audit.getReferenceId()) &&
                   Status.SUCCESS.equals(audit.getOutcome().getStatus());
        }));
    }

    @Test
    public void shouldDeleteByDomainAndUserAndId_notFound() {
        // Test case where credential is not found (doesn't exist or belongs to different user/domain)
        when(certificateCredentialRepository.deleteByDomainAndUserAndId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID), eq("credential-id")))
                .thenReturn(Maybe.empty());

        TestObserver<CertificateCredential> testObserver = certificateCredentialService
                .deleteByDomainAndUserAndId(DOMAIN, USER_ID, "credential-id", principal)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();

        verify(certificateCredentialRepository, times(1)).deleteByDomainAndUserAndId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID), eq("credential-id"));
        // Verify audit logging is NOT called when credential not found (client error)
        verify(auditService, never()).report(any(AuditBuilder.class));
    }

    @Test
    public void shouldDeleteByUserId() {
        when(certificateCredentialRepository.deleteByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID)))
                .thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        TestObserver<Void> testObserver = certificateCredentialService
                .deleteByUserId(DOMAIN, USER_ID)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateCredentialRepository, times(1)).deleteByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID));
    }

    @Test
    public void shouldDeleteByDomain() {
        when(certificateCredentialRepository.deleteByReference(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId())))
                .thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        TestObserver<Void> testObserver = certificateCredentialService
                .deleteByDomain(DOMAIN)
                .test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateCredentialRepository, times(1)).deleteByReference(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()));
    }

    // Helper methods

    /**
     * Create a list of certificate credentials for testing.
     *
     * @param count the number of credentials to create
     * @return a list of certificate credentials
     */
    private List<CertificateCredential> createCredentialsList(int count) {
        List<CertificateCredential> credentials = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CertificateCredential cred = CertificateCredentialTestFixtures.buildCertificateCredential(
                    DOMAIN, USER_ID, VALID_PEM_CERT);
            cred.setId("cred-" + i);
            credentials.add(cred);
        }
        return credentials;
    }

    /**
     * Setup mocks for successful enrollment (no duplicates, within limit).
     */
    private void setupSuccessfulEnrollmentMocks() {
        when(certificateCredentialRepository.findByThumbprint(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), anyString()))
                .thenReturn(Maybe.empty());
        when(certificateCredentialRepository.findByUserId(
                eq(ReferenceType.DOMAIN), eq(DOMAIN.getId()), eq(USER_ID)))
                .thenReturn(Flowable.empty());
    }
}

