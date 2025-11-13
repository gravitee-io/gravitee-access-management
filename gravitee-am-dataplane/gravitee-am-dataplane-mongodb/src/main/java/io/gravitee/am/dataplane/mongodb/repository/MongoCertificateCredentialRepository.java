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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.CertificateCredentialRepository;
import io.gravitee.am.dataplane.mongodb.repository.model.CertificateCredentialMongo;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoCertificateCredentialRepository extends AbstractDataPlaneMongoRepository implements CertificateCredentialRepository {

    private static final String COLLECTION_NAME = "cert_credentials";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CERTIFICATE_THUMBPRINT = "certificateThumbprint";
    private static final String FIELD_CERTIFICATE_SUBJECT_DN = "certificateSubjectDN";
    private static final String FIELD_CERTIFICATE_SERIAL_NUMBER = "certificateSerialNumber";
    private static final String FIELD_CERTIFICATE_EXPIRES_AT = "certificateExpiresAt";

    private MongoCollection<CertificateCredentialMongo> certificateCredentialsCollection;

    @PostConstruct
    public void init() {
        certificateCredentialsCollection = mongoDatabase.getCollection(COLLECTION_NAME, CertificateCredentialMongo.class);
        MongoUtils.init(certificateCredentialsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        // Index for findByUserId
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("rt1ri1uid1"));
        // Index for findByThumbprint
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CERTIFICATE_THUMBPRINT, 1), new IndexOptions().name("rt1ri1ct1"));
        // Index for findBySubjectDN
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CERTIFICATE_SUBJECT_DN, 1), new IndexOptions().name("rt1ri1csdn1"));
        // Index for findBySerialNumber
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CERTIFICATE_SERIAL_NUMBER, 1), new IndexOptions().name("rt1ri1csn1"));
        // Index for findExpiredCertificates
        indexes.put(new Document(FIELD_CERTIFICATE_EXPIRES_AT, 1), new IndexOptions().name("cea1"));

        super.createIndex(certificateCredentialsCollection, indexes);
    }

    @Override
    public Maybe<CertificateCredential> findById(String id) {
        return Observable.fromPublisher(certificateCredentialsCollection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CertificateCredential> create(CertificateCredential item) {
        CertificateCredentialMongo credential = convert(item);
        credential.setId(credential.getId() == null ? RandomString.generate() : credential.getId());
        return Single.fromPublisher(certificateCredentialsCollection.insertOne(credential))
                .flatMap(success -> {
                    item.setId(credential.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CertificateCredential> update(CertificateCredential item) {
        CertificateCredentialMongo credential = convert(item);
        return Single.fromPublisher(certificateCredentialsCollection.replaceOne(eq(FIELD_ID, credential.getId()), credential))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(certificateCredentialsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Flowable.fromPublisher(
                certificateCredentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USER_ID, userId)
                        )
                ))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> findByThumbprint(ReferenceType referenceType, String referenceId, String thumbprint) {
        return Observable.fromPublisher(
                certificateCredentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CERTIFICATE_THUMBPRINT, thumbprint)
                        )
                ).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findBySubjectDN(ReferenceType referenceType, String referenceId, String subjectDN) {
        return Flowable.fromPublisher(
                certificateCredentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CERTIFICATE_SUBJECT_DN, subjectDN)
                        )
                ))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> findBySerialNumber(ReferenceType referenceType, String referenceId, String serialNumber) {
        return Observable.fromPublisher(
                certificateCredentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CERTIFICATE_SERIAL_NUMBER, serialNumber)
                        )
                ).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CertificateCredential> findExpiredCertificates(ReferenceType referenceType, String referenceId) {
        Date now = new Date();
        return Flowable.fromPublisher(
                certificateCredentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                lt(FIELD_CERTIFICATE_EXPIRES_AT, now)
                        )
                ))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Completable.fromPublisher(
                certificateCredentialsCollection.deleteMany(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USER_ID, userId)
                        )
                ))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CertificateCredential> deleteByDomainAndUserAndId(ReferenceType referenceType, String referenceId, String userId, String credentialId) {
        return Observable.fromPublisher(
                certificateCredentialsCollection.findOneAndDelete(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USER_ID, userId),
                                eq(FIELD_ID, credentialId)
                        )
                ))
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(
                certificateCredentialsCollection.deleteMany(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId)
                        )
                ))
                .observeOn(Schedulers.computation());
    }

    private CertificateCredential convert(CertificateCredentialMongo credentialMongo) {
        if (credentialMongo == null) {
            return null;
        }

        CertificateCredential credential = new CertificateCredential();
        credential.setId(credentialMongo.getId());
        credential.setReferenceType(credentialMongo.getReferenceType());
        credential.setReferenceId(credentialMongo.getReferenceId());
        credential.setUserId(credentialMongo.getUserId());
        credential.setUsername(credentialMongo.getUsername());
        credential.setIpAddress(credentialMongo.getIpAddress());
        credential.setUserAgent(credentialMongo.getUserAgent());
        credential.setDeviceName(credentialMongo.getDeviceName());
        credential.setCertificatePem(credentialMongo.getCertificatePem());
        credential.setCertificateThumbprint(credentialMongo.getCertificateThumbprint());
        credential.setCertificateSubjectDN(credentialMongo.getCertificateSubjectDN());
        credential.setCertificateSerialNumber(credentialMongo.getCertificateSerialNumber());
        credential.setCertificateExpiresAt(credentialMongo.getCertificateExpiresAt());
        credential.setCreatedAt(credentialMongo.getCreatedAt());
        credential.setUpdatedAt(credentialMongo.getUpdatedAt());
        credential.setAccessedAt(credentialMongo.getAccessedAt());

        // Convert Document metadata to Map
        if (credentialMongo.getMetadata() != null) {
            Map<String, Object> metadata = new HashMap<>();
            credentialMongo.getMetadata().forEach(metadata::put);
            credential.setMetadata(metadata);
        }

        return credential;
    }

    private CertificateCredentialMongo convert(CertificateCredential credential) {
        if (credential == null) {
            return null;
        }

        CertificateCredentialMongo credentialMongo = new CertificateCredentialMongo();
        credentialMongo.setId(credential.getId());
        credentialMongo.setReferenceType(credential.getReferenceType());
        credentialMongo.setReferenceId(credential.getReferenceId());
        credentialMongo.setUserId(credential.getUserId());
        credentialMongo.setUsername(credential.getUsername());
        credentialMongo.setIpAddress(credential.getIpAddress());
        credentialMongo.setUserAgent(credential.getUserAgent());
        credentialMongo.setDeviceName(credential.getDeviceName());
        credentialMongo.setCertificatePem(credential.getCertificatePem());
        credentialMongo.setCertificateThumbprint(credential.getCertificateThumbprint());
        credentialMongo.setCertificateSubjectDN(credential.getCertificateSubjectDN());
        credentialMongo.setCertificateSerialNumber(credential.getCertificateSerialNumber());
        credentialMongo.setCertificateExpiresAt(credential.getCertificateExpiresAt());
        credentialMongo.setCreatedAt(credential.getCreatedAt());
        credentialMongo.setUpdatedAt(credential.getUpdatedAt());
        credentialMongo.setAccessedAt(credential.getAccessedAt());

        // Convert Map metadata to Document
        if (credential.getMetadata() != null) {
            credentialMongo.setMetadata(new Document(credential.getMetadata()));
        }

        return credentialMongo;
    }
}

