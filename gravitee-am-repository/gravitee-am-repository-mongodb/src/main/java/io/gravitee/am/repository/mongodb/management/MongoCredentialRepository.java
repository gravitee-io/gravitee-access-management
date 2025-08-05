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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.CredentialMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoCredentialRepository extends AbstractManagementMongoRepository implements CredentialRepository {
    // TODO [DP] class to remove
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_CREDENTIAL_ID = "credentialId";
    private static final String FIELD_AAGUID = "aaguid";
    private static final String FIELD_CREATED_AT = "createdAt";

    private MongoCollection<CredentialMongo> credentialsCollection;

    private final Set<String> UNUSED_INDEXES = Set.of(
            "rt1ri1",
            "rt1ri1un1"
    );

    @PostConstruct
    public void init() {
        credentialsCollection = mongoOperations.getCollection("webauthn_credentials", CredentialMongo.class);
        super.init(credentialsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("rt1ri1uid1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USERNAME, 1).append(FIELD_CREATED_AT, -1), new IndexOptions().name("rt1ri1un1c_1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CREDENTIAL_ID, 1), new IndexOptions().name("rt1ri1cid1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_AAGUID, 1), new IndexOptions().name("rt1ri1a1"));

        super.createIndex(credentialsCollection, indexes);
        if (ensureIndexOnStart) {
            dropIndexes(credentialsCollection, UNUSED_INDEXES::contains).subscribe();
        }
    }

    @Override
    public Flowable<Credential> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Flowable.fromPublisher(
                credentialsCollection.find(
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
    public Flowable<Credential> findByUsername(ReferenceType referenceType, String referenceId, String username) {
        return Flowable.fromPublisher(
                credentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USERNAME, username)
                        )
                ))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Credential> findByUsername(ReferenceType referenceType, String referenceId, String username, int limit) {
        return Flowable.fromPublisher(
                        credentialsCollection.find(
                                and(
                                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                        eq(FIELD_REFERENCE_ID, referenceId),
                                        eq(FIELD_USERNAME, username)
                                )).sort(
                                    new Document(FIELD_CREATED_AT, -1))
                                .limit(limit))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Credential> findByCredentialId(ReferenceType referenceType, String referenceId, String credentialId) {
        return Flowable.fromPublisher(
                credentialsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CREDENTIAL_ID, credentialId)
                        )
                ))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Credential> findById(String id) {
        return Observable.fromPublisher(credentialsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Credential> create(Credential item) {
        CredentialMongo credential = convert(item);
        credential.setId(credential.getId() == null ? RandomString.generate() : credential.getId());
        return Single.fromPublisher(credentialsCollection.insertOne(credential)).flatMap(success -> { item.setId(credential.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Credential> update(Credential item) {
        CredentialMongo credential = convert(item);
        return Single.fromPublisher(credentialsCollection.replaceOne(eq(FIELD_ID, credential.getId()), credential)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(credentialsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return Completable.fromPublisher(
                credentialsCollection.deleteMany(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_USER_ID, userId)
                        )
                ))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(
                credentialsCollection.deleteMany(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId)
                        )
                ))
                .observeOn(Schedulers.computation());
    }

    private Credential convert(CredentialMongo credentialMongo) {
        if (credentialMongo == null) {
            return null;
        }

        Credential credential = new Credential();
        credential.setId(credentialMongo.getId());
        credential.setReferenceType(credentialMongo.getReferenceType());
        credential.setReferenceId(credentialMongo.getReferenceId());
        credential.setUserId(credentialMongo.getUserId());
        credential.setUsername(credentialMongo.getUsername());
        credential.setCredentialId(credentialMongo.getCredentialId());
        credential.setPublicKey(credentialMongo.getPublicKey());
        credential.setCounter(credentialMongo.getCounter());
        credential.setAaguid(credentialMongo.getAaguid());
        credential.setAttestationStatementFormat(credentialMongo.getAttestationStatementFormat());
        credential.setAttestationStatement(credentialMongo.getAttestationStatement());
        credential.setIpAddress(credentialMongo.getIpAddress());
        credential.setUserAgent(credentialMongo.getUserAgent());
        credential.setDeviceName(credentialMongo.getDeviceName());
        credential.setCreatedAt(credentialMongo.getCreatedAt());
        credential.setUpdatedAt(credentialMongo.getUpdatedAt());
        credential.setAccessedAt(credentialMongo.getAccessedAt());
        credential.setLastCheckedAt(credentialMongo.getLastCheckedAt());
        return credential;
    }

    private CredentialMongo convert(Credential credential) {
        if (credential == null) {
            return null;
        }

        CredentialMongo credentialMongo = new CredentialMongo();
        credentialMongo.setId(credential.getId());
        credentialMongo.setReferenceType(credential.getReferenceType());
        credentialMongo.setReferenceId(credential.getReferenceId());
        credentialMongo.setUserId(credential.getUserId());
        credentialMongo.setUsername(credential.getUsername());
        credentialMongo.setCredentialId(credential.getCredentialId());
        credentialMongo.setPublicKey(credential.getPublicKey());
        if (credential.getCounter() != null) {
            credentialMongo.setCounter(credential.getCounter());
        }
        credentialMongo.setAaguid(credential.getAaguid());
        credentialMongo.setAttestationStatementFormat(credential.getAttestationStatementFormat());
        credentialMongo.setAttestationStatement(credential.getAttestationStatement());
        credentialMongo.setIpAddress(credential.getIpAddress());
        credentialMongo.setUserAgent(credential.getUserAgent());
        credentialMongo.setDeviceName(credential.getDeviceName());
        credentialMongo.setCreatedAt(credential.getCreatedAt());
        credentialMongo.setUpdatedAt(credential.getUpdatedAt());
        credentialMongo.setAccessedAt(credential.getAccessedAt());
        credentialMongo.setLastCheckedAt(credential.getLastCheckedAt());
        return credentialMongo;
    }

}
