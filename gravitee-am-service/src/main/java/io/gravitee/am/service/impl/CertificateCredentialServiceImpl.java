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
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.repository.management.api.CertificateCredentialRepository;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.CertificateCredentialService;
import io.gravitee.am.service.exception.CertificateExpiredException;
import io.gravitee.am.service.exception.CertificateLimitExceededException;
import io.gravitee.am.service.exception.DuplicateCertificateException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
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

    @Lazy
    @Autowired
    private CertificateCredentialRepository certificateCredentialRepository;

    @Value("${users.certificate.maxCertificatesPerUser:20}")
    private int maxCertificatesPerUser;

    @Override
    public Single<CertificateCredential> enrollCertificate(Domain domain, String userId, String certificatePem, String deviceName) {
        log.debug("Enroll certificate for domain {} and user {}", domain.getId(), userId);
        try {
            // 1. Parse PEM certificate
            X509Certificate cert = X509CertUtils.parseWithException(certificatePem);
            if (cert == null) {
                log.error("Failed to parse certificate for domain {} and user {}", domain.getId(), userId);
                return Single.error(new TechnicalManagementException("Failed to parse certificate"));
            }

            // 2. Extract certificate fields
            String thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");
            String subjectDN = cert.getSubjectX500Principal().getName();
            String serialNumber = cert.getSerialNumber().toString();
            Date expiresAt = cert.getNotAfter();
            log.debug("Certificate parsed successfully - Subject DN: {}, Serial: {}, Thumbprint: {}", subjectDN, serialNumber, thumbprint);

            // 3. Validate certificate
            // Check if expired
            if (expiresAt.before(new Date())) {
                log.warn("Certificate enrollment rejected - certificate expired for domain {} and user {}", domain.getId(), userId);
                return Single.error(new CertificateExpiredException("Certificate has expired"));
            }

            // Check if duplicate (by thumbprint)
            return certificateCredentialRepository
                    .findByThumbprint(ReferenceType.DOMAIN, domain.getId(), thumbprint)
                    .flatMapSingle(existing -> {
                        log.warn("Certificate enrollment rejected - duplicate thumbprint for domain {} and user {} (thumbprint: {})", 
                                domain.getId(), userId, thumbprint);
                        return Single.<CertificateCredential>error(
                                new DuplicateCertificateException("Certificate with this thumbprint already exists"));
                    })
                    .switchIfEmpty(
                            // 4. Check certificate limit
                            certificateCredentialRepository
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

                                        // 5. Create CertificateCredential
                                        CertificateCredential credential = new CertificateCredential();
                                        credential.setReferenceType(ReferenceType.DOMAIN);
                                        credential.setReferenceId(domain.getId());
                                        credential.setUserId(userId);
                                        credential.setCertificatePem(certificatePem);
                                        credential.setCertificateThumbprint(thumbprint);
                                        credential.setCertificateSubjectDN(subjectDN);
                                        credential.setCertificateSerialNumber(serialNumber);
                                        credential.setCertificateExpiresAt(expiresAt);
                                        credential.setDeviceName(deviceName);

                                        // Set metadata (optional fields)
                                        Map<String, Object> metadata = new HashMap<>();
                                        metadata.put("issuerDN", cert.getIssuerX500Principal().getName());
                                        credential.setMetadata(metadata);

                                        // Set timestamps
                                        Date now = new Date();
                                        credential.setCreatedAt(now);
                                        credential.setUpdatedAt(now);

                                        // 6. Store in repository
                                        log.debug("Creating certificate credential for domain {} and user {} with thumbprint {}", 
                                                domain.getId(), userId, thumbprint);
                                        return certificateCredentialRepository.create(credential)
                                                .doOnSuccess(created -> log.debug("Certificate credential created successfully with ID {} for domain {} and user {}", 
                                                        created.getId(), domain.getId(), userId));
                                    })
                    );
        } catch (CertificateEncodingException e) {
            log.error("Failed to calculate certificate thumbprint", e);
            return Single.error(new TechnicalManagementException("Failed to calculate certificate thumbprint: " + e.getMessage()));
        } catch (CertificateException e) {
            log.error("Failed to parse certificate", e);
            return Single.error(new TechnicalManagementException("Failed to parse certificate: " + e.getMessage()));
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate certificate thumbprint", e);
            return Single.error(new TechnicalManagementException("Failed to calculate certificate thumbprint: " + e.getMessage()));
        }
    }

    @Override
    public Flowable<CertificateCredential> findByUserId(Domain domain, String userId) {
        log.debug("Find certificate credentials for domain {} and user {}", domain.getId(), userId);
        return certificateCredentialRepository
                .findByUserId(ReferenceType.DOMAIN, domain.getId(), userId)
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credentials for user {}", userId, error);
                    return Flowable.error(new TechnicalManagementException("Failed to find certificate credentials", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> findById(Domain domain, String id) {
        log.debug("Find certificate credential by ID {} for domain {}", id, domain.getId());
        return certificateCredentialRepository
                .findById(id)
                .onErrorResumeNext(error -> {
                    log.error("Failed to find certificate credential {}", id, error);
                    return Maybe.error(new TechnicalManagementException("Failed to find certificate credential", error));
                });
    }

    @Override
    public Completable delete(Domain domain, String id) {
        log.debug("Delete certificate credential {} for domain {}", id, domain.getId());
        return certificateCredentialRepository
                .delete(id)
                .doOnComplete(() -> log.debug("Certificate credential {} deleted successfully for domain {}", id, domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credential {}", id, error);
                    return Completable.error(new TechnicalManagementException("Failed to delete certificate credential", error));
                });
    }

    @Override
    public Maybe<CertificateCredential> deleteByDomainAndUserAndId(Domain domain, String userId, String credentialId) {
        log.debug("Delete certificate credential {} for domain {} and user {}", credentialId, domain.getId(), userId);
        return certificateCredentialRepository
                .deleteByDomainAndUserAndId(ReferenceType.DOMAIN, domain.getId(), userId, credentialId)
                .doOnSuccess(credential -> log.debug("Certificate credential {} deleted successfully for domain {} and user {}",
                        credentialId, domain.getId(), userId))
                .doOnComplete(() -> log.debug("Certificate credential {} not found or does not belong to user {} in domain {}",
                        credentialId, userId, domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credential {} for user {} in domain {}", credentialId, userId, domain.getId(), error);
                    return Maybe.error(new TechnicalManagementException("Failed to delete certificate credential", error));
                });
    }

    @Override
    public Completable deleteByUserId(Domain domain, String userId) {
        log.debug("Delete all certificate credentials for domain {} and user {}", domain.getId(), userId);
        return certificateCredentialRepository
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
        return certificateCredentialRepository
                .deleteByReference(ReferenceType.DOMAIN, domain.getId())
                .doOnComplete(() -> log.debug("All certificate credentials deleted successfully for domain {}", domain.getId()))
                .onErrorResumeNext(error -> {
                    log.error("Failed to delete certificate credentials for domain {}", domain.getId(), error);
                    return Completable.error(new TechnicalManagementException("Failed to delete certificate credentials", error));
                });
    }
}

