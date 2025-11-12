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
package io.gravitee.am.dataplane.jdbc.repository;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.CertificateCredentialRepository;
import io.gravitee.am.dataplane.jdbc.repository.model.JdbcCertificateCredential;
import io.gravitee.am.dataplane.jdbc.repository.spring.SpringCertificateCredentialRepository;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Component
public class JdbcCertificateCredentialRepository extends AbstractJdbcRepository implements CertificateCredentialRepository {
    @Autowired
    private SpringCertificateCredentialRepository certificateCredentialRepository;

    protected CertificateCredential toEntity(JdbcCertificateCredential entity) {
        return mapper.map(entity, CertificateCredential.class);
    }

    protected JdbcCertificateCredential toJdbcEntity(CertificateCredential entity) {
        return mapper.map(entity, JdbcCertificateCredential.class);
    }

    @Override
    public Maybe<CertificateCredential> findById(String id) {
        LOGGER.debug("findById({})", id);
        return certificateCredentialRepository.findById(id)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve certificate credential for Id {}", id, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CertificateCredential> create(CertificateCredential item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        // Set timestamps if not already set (required for database NOT NULL constraint)
        Date now = new Date();
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(now);
        }
        if (item.getUpdatedAt() == null) {
            item.setUpdatedAt(now);
        }
        LOGGER.debug("create certificate credential with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CertificateCredential> update(CertificateCredential item) {
        LOGGER.debug("update certificate credential with id {}", item.getId());
        // Fetch existing record to preserve createdAt (required due to NOT NULL constraint on created_at column).
        // This is defensive programming - service layer should preserve createdAt, but this ensures data integrity.
        return findById(item.getId())
                .toSingle()
                .flatMap(existing -> {
                    // Preserve createdAt from existing record if not set in update
                    if (item.getCreatedAt() == null) {
                        item.setCreatedAt(existing.getCreatedAt());
                    }
                    // Set updatedAt timestamp
                    item.setUpdatedAt(new Date());
                    return certificateCredentialRepository.save(toJdbcEntity(item))
                            .map(this::toEntity);
                })
                .doOnError(error -> LOGGER.error("unable to update certificate credential with id {}", item.getId(), error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return certificateCredentialRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete certificate credential for Id {}", id, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("findByUserId({},{},{})", referenceType, referenceId, userId);
        return certificateCredentialRepository.findByUserId(referenceType.name(), referenceId, userId)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> findByThumbprint(ReferenceType referenceType, String referenceId, String thumbprint) {
        LOGGER.debug("findByThumbprint({},{},{})", referenceType, referenceId, thumbprint);
        return Observable.fromPublisher(certificateCredentialRepository.findByThumbprint(referenceType.name(), referenceId, thumbprint))
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findBySubjectDN(ReferenceType referenceType, String referenceId, String subjectDN) {
        LOGGER.debug("findBySubjectDN({},{},{})", referenceType, referenceId, subjectDN);
        return certificateCredentialRepository.findBySubjectDN(referenceType.name(), referenceId, subjectDN)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> findBySerialNumber(ReferenceType referenceType, String referenceId, String serialNumber) {
        LOGGER.debug("findBySerialNumber({},{},{})", referenceType, referenceId, serialNumber);
        return Observable.fromPublisher(certificateCredentialRepository.findBySerialNumber(referenceType.name(), referenceId, serialNumber))
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findExpiredCertificates(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findExpiredCertificates({},{})", referenceType, referenceId);
        LocalDateTime now = LocalDateTime.now();
        return certificateCredentialRepository.findExpiredCertificates(referenceType.name(), referenceId, now)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("deleteByUserId({},{},{})", referenceType, referenceId, userId);
        return monoToCompletable(getTemplate().delete(JdbcCertificateCredential.class)
                .matching(Query.query(
                        where("reference_type").is(referenceType.name())
                                .and(where("reference_id").is(referenceId))
                                .and(where("user_id").is(userId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete certificate credentials for userId {}", userId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> deleteByDomainAndUserAndId(ReferenceType referenceType, String referenceId, String userId, String credentialId) {
        LOGGER.debug("deleteByDomainAndUserAndId({},{},{},{})", referenceType, referenceId, userId, credentialId);
        // Fetch the credential first to return it after deletion
        return certificateCredentialRepository.findByReferenceTypeAndReferenceIdAndUserIdAndId(
                        referenceType.name(), referenceId, userId, credentialId)
                .map(this::toEntity)
                .flatMap(credential -> {
                    // Delete atomically with all conditions in WHERE clause
                    // This ensures we only delete if all conditions match (prevents race conditions)
                    return monoToSingle(getTemplate().delete(JdbcCertificateCredential.class)
                            .matching(Query.query(
                                    where("reference_type").is(referenceType.name())
                                            .and(where("reference_id").is(referenceId))
                                            .and(where("user_id").is(userId))
                                            .and(where("id").is(credentialId))))
                            .all())
                            .flatMapMaybe(rowsDeleted -> rowsDeleted == 0
                                    ? Maybe.empty()  // Credential was already deleted (race condition)
                                    : Maybe.just(credential));
                })
                .doOnError(error -> LOGGER.error("Unable to delete certificate credential {} for user {} in domain {}",
                        credentialId, userId, referenceId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({},{})", referenceType, referenceId);
        return monoToCompletable(getTemplate().delete(JdbcCertificateCredential.class)
                .matching(Query.query(
                        where("reference_type").is(referenceType.name())
                                .and(where("reference_id").is(referenceId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete certificate credentials for reference {} - {}", referenceType.name(), referenceId, error))
                .observeOn(Schedulers.computation());
    }
}

