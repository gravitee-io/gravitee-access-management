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
package io.gravitee.am.service.impl;

import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificateCredentialService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateCredentialAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class CertificateCredentialServiceImpl implements CertificateCredentialService {

    @Autowired
    private AuditService auditService;

    @Autowired
    @Lazy
    private DataPlaneRegistry dataPlaneRegistry;

    @Value("${users.certificate.maxCertificatesPerUser:20}")
    private int maxCertificatesPerUser;

    @Override
    public Single<CertificateCredential> enrollCertificate(Domain domain, String userId, String certificatePem, User principal) {
        log.debug("Enroll certificate for domain {} and user {}", domain.getId(), userId);
        try {
            CertificateFields certFields = parseCertificate(certificatePem);
            return dataPlaneRegistry.getUserRepository(domain)
                    .findById(userId)
                    .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                    .concatMap(user -> validateCertificateNotExpired(certFields.expiresAt, domain, userId)
                            .andThen(Single.defer(() -> checkDuplicateCertificate(certFields.thumbprint, domain)
                                    .switchIfEmpty(
                                            checkCertificateLimit(domain, userId)
                                                    .flatMap(count -> createAndStoreCredential(
                                                            domain, user, certificatePem, certFields))
                                                    .toMaybe()
                                    )
                                    .toSingle()))
                            .doOnSuccess(credential -> reportAuditSuccess(principal, credential))
                            .doOnError(throwable -> reportAuditError(principal, domain.getId(), throwable)));
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            log.error("Failed to calculate certificate thumbprint", e);
            return Single.error(new TechnicalManagementException("Failed to calculate certificate thumbprint: " + e.getMessage()));
        } catch (CertificateException e) {
            log.error("Failed to parse certificate", e);
            return Single.error(new TechnicalManagementException("Failed to parse certificate: " + e.getMessage()));
        }
    }

    private CertificateFields parseCertificate(String certificatePem) throws CertificateException, NoSuchAlgorithmException {
        X509Certificate cert = X509CertUtils.parseWithException(certificatePem);
        if (cert == null) {
            throw new CertificateException("Failed to parse certificate");
        }

        String thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");
        String subjectDN = new X500Name(cert.getSubjectX500Principal().getName()).toString();
        String issuerDN = new X500Name(cert.getIssuerX500Principal().getName()).toString();
        String serialNumber = cert.getSerialNumber().toString();
        Date expiresAt = cert.getNotAfter();

        log.debug("Certificate parsed successfully - Subject DN: {}, Serial: {}, Thumbprint: {}", subjectDN, serialNumber, thumbprint);
        
        return new CertificateFields(thumbprint, subjectDN, serialNumber, expiresAt, issuerDN);
    }

    /**
     * Validates that the certificate is not expired.
     * Returns a Completable that completes if valid, or errors if expired.
     */
    private Completable validateCertificateNotExpired(Date expiresAt, Domain domain, String userId) {
        if (expiresAt.before(new Date())) {
            log.warn("Certificate enrollment rejected - certificate expired for domain {} and user {}", domain.getId(), userId);
            return Completable.error(new CertificateExpiredException("Certificate has expired"));
        }
        return Completable.complete();
    }

    /**
     * Checks if a certificate with the given thumbprint already exists.
     * Returns Maybe.empty() if no duplicate (chain continues), or Maybe.error() if duplicate found.
     */
    private Maybe<CertificateCredential> checkDuplicateCertificate(String thumbprint, Domain domain) {
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findByThumbprint(ReferenceType.DOMAIN, domain.getId(), thumbprint)
                .flatMapSingle(existing -> {
                    log.warn("Certificate enrollment rejected - duplicate thumbprint for domain {} (thumbprint: {})", 
                            domain, thumbprint);
                    return Single.<CertificateCredential>error(
                            new DuplicateCertificateException("Certificate with this thumbprint already exists"));
                });
    }

    /**
     * Checks if the user has reached the certificate limit.
     * Returns a Single with the current count if within limit, error otherwise.
     * <p>
     * Note: There is a small TOCTOU race condition window where concurrent enrollments
     * could exceed the limit by 1. This is acceptable for this use case and consistent
     * with similar patterns in the codebase (e.g., OrganizationUserServiceImpl.generateAccountAccessToken).
     */
    private Single<Long> checkCertificateLimit(Domain domain, String userId) {
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findByUserId(ReferenceType.DOMAIN, domain.getId(), userId)
                .count()
                .flatMap(count -> {
                    log.debug("Current certificate count for user {}: {}, limit: {}", userId, count, maxCertificatesPerUser);
                    if (count >= maxCertificatesPerUser) {
                        log.warn("Certificate enrollment rejected - limit exceeded for domain {} and user {} (count: {}, limit: {})",
                                domain.getId(), userId, count, maxCertificatesPerUser);
                        return Single.error(new CertificateLimitExceededException(
                                String.format("Maximum number of certificates (%d) exceeded for user", maxCertificatesPerUser)));
                    }
                    return Single.just(count);
                });
    }

    private Single<CertificateCredential> createAndStoreCredential(
            Domain domain, io.gravitee.am.model.User user, String certificatePem, CertificateFields certFields) {
        CertificateCredential credential = buildCertificateCredential(
                domain, user, certificatePem, certFields);
        
        log.debug("Creating certificate credential for domain {} and user {} with thumbprint {}", 
                domain.getId(), user.getId(), certFields.thumbprint);
        
        return dataPlaneRegistry.getCertificateCredentialRepository(domain).create(credential)
                .doOnSuccess(created -> log.debug("Certificate credential created successfully with ID {} for domain {} and user {}",
                        created.getId(), domain.getId(), user.getId()));
    }

    private CertificateCredential buildCertificateCredential(
            Domain domain, io.gravitee.am.model.User user, String certificatePem, CertificateFields certFields) {
        CertificateCredential credential = new CertificateCredential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(domain.getId());
        credential.setUserId(user.getId());
        credential.setUsername(user.getUsername());
        credential.setCertificatePem(certificatePem);
        credential.setCertificateThumbprint(certFields.thumbprint);
        credential.setCertificateSubjectDN(certFields.subjectDN);
        credential.setCertificateSerialNumber(certFields.serialNumber);
        credential.setCertificateExpiresAt(certFields.expiresAt);
        credential.setCertificateIssuerDN(certFields.issuerDN);

        Date now = new Date();
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);

        return credential;
    }

    private void reportAuditSuccess(User principal, CertificateCredential credential) {
        auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                .principal(principal)
                .type(EventType.CREDENTIAL_CREATED)
                .certificateCredential(credential));
    }

    private void reportAuditError(User principal, String domainId, Throwable throwable) {
        auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                .principal(principal)
                .type(EventType.CREDENTIAL_CREATED)
                .reference(Reference.domain(domainId))
                .throwable(throwable));
    }

    private static class CertificateFields {
        final String thumbprint;
        final String subjectDN;
        final String serialNumber;
        final Date expiresAt;
        final String issuerDN;

        CertificateFields(String thumbprint, String subjectDN, 
                         String serialNumber, Date expiresAt, String issuerDN) {
            this.thumbprint = thumbprint;
            this.subjectDN = subjectDN;
            this.serialNumber = serialNumber;
            this.expiresAt = expiresAt;
            this.issuerDN = issuerDN;
        }
    }

    @Override
    public Flowable<CertificateCredential> findByUserId(Domain domain, String userId) {
        log.debug("Find certificate credentials for domain {} and user {}", domain.getId(), userId);
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findByUserId(ReferenceType.DOMAIN, domain.getId(), userId)
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credentials for user {}", userId, error);
                    return Flowable.error(new TechnicalManagementException("Failed to find certificate credentials", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> findByThumbprint(Domain domain, String thumbprint) {
        log.debug("Find certificate credentials for domain {} and thumbprint {}", domain.getId(), thumbprint);
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findByThumbprint(ReferenceType.DOMAIN, domain.getId(), thumbprint)
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credentials for thumbprint {}", thumbprint, error);
                    return Maybe.error(new TechnicalManagementException("Failed to find certificate credentials", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> findByPrimaryMetadata(Domain domain, String subjectDN, String issuerDN, String serialNumber) {
        log.debug("Find certificate credentials for domain {} and subject {} issuer {} serialNumber {}", domain.getId(), subjectDN, issuerDN, serialNumber);
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findBySubjectAndIssuerAndSerialNumber(ReferenceType.DOMAIN, domain.getId(), subjectDN, issuerDN, serialNumber)
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credentials for subject {} issuer {} serialNumber {}", subjectDN, issuerDN, serialNumber, error);
                    return Maybe.error(new TechnicalManagementException("Failed to find certificate credentials", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> findById(Domain domain, String id) {
        log.debug("Find certificate credential by ID {} for domain {}", id, domain.getId());
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .findById(id)
                .filter(cred -> cred.getReferenceType() == ReferenceType.DOMAIN && cred.getReferenceId().equals(domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credential {}", id, error);
                    return Maybe.error(new TechnicalManagementException("Failed to find certificate credential", error));
                });
    }

    @Override
    public Completable delete(Domain domain, String id) {
        log.debug("Delete certificate credential {} for domain {}", id, domain.getId());
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .delete(id)
                .doOnComplete(() -> log.debug("Certificate credential {} deleted successfully for domain {}", id, domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credential {}", id, error);
                    return Completable.error(new TechnicalManagementException("Failed to delete certificate credential", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> deleteByDomainAndUserAndId(Domain domain, String userId, String credentialId, User principal) {
        log.debug("Delete certificate credential {} for domain {} and user {}", credentialId, domain.getId(), userId);
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, domain.getId(), userId, credentialId)
                .doOnSuccess(credential -> {
                    log.debug("Certificate credential {} deleted successfully for domain {} and user {}",
                            credentialId, domain.getId(), userId);
                    auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                            .principal(principal)
                            .type(EventType.CREDENTIAL_DELETED)
                            .certificateCredential(credential));
                })
                .doOnComplete(() -> log.debug("Certificate credential {} not found or does not belong to user {} in domain {}",
                        credentialId, userId, domain.getId()))
                .doOnError(throwable -> {
                    // Only log audit for technical errors, not for client errors
                    if (!(throwable instanceof CredentialNotFoundException) && !(throwable instanceof DomainNotFoundException)) {
                        auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.CREDENTIAL_DELETED)
                                .reference(Reference.domain(domain.getId()))
                                .throwable(throwable));
                    }
                })
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credential {} for user {} in domain {}", credentialId, userId, domain.getId(), error);
                    return Maybe.error(new TechnicalManagementException("Failed to delete certificate credential", error));
                });
    }

    @Override
    public Completable deleteByUserId(Domain domain, String userId) {
        log.debug("Delete all certificate credentials for domain {} and user {}", domain.getId(), userId);
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .deleteByUserId(ReferenceType.DOMAIN, domain.getId(), userId)
                .doOnComplete(() -> log.debug("All certificate credentials deleted successfully for domain {} and user {}",
                        domain.getId(), userId))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credentials for user {} in domain {}", userId, domain.getId(), error);
                    return Completable.error(new TechnicalManagementException("Failed to delete certificate credentials", error));
                });
    }

    @Override
    public Completable deleteByDomain(Domain domain) {
        log.debug("Delete all certificate credentials for domain {}", domain.getId());
        return dataPlaneRegistry.getCertificateCredentialRepository(domain)
                .deleteByReference(ReferenceType.DOMAIN, domain.getId())
                .doOnComplete(() -> log.debug("All certificate credentials deleted successfully for domain {}", domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credentials for domain {}", domain.getId(), error);
                    return Completable.error(new TechnicalManagementException("Failed to delete certificate credentials", error));
                });
    }
}

