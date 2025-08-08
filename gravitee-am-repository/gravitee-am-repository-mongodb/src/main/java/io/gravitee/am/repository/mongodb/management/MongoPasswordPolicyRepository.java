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
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordPolicyMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoPasswordPolicyRepository extends AbstractManagementMongoRepository implements PasswordPolicyRepository {
    private static final String COLLECTION_NAME = "password_policies";
    private static final String CREATED_AT = "createdAt";
    private static final String FIELD_DEFAULT_POLICY = "defaultPolicy";
    private MongoCollection<PasswordPolicyMongo> mongoCollection;

    @PostConstruct
    public void init() {
        mongoCollection = mongoOperations.getCollection(COLLECTION_NAME, PasswordPolicyMongo.class);
        super.init(mongoCollection);

        super.createIndex(mongoCollection, Map.of(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1")));
    }

    @Override
    public Maybe<PasswordPolicy> findById(String id) {
        return Observable.fromPublisher(mongoCollection.find(and(eq(FIELD_ID, id))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PasswordPolicy> create(PasswordPolicy policy) {
        PasswordPolicyMongo policyMongo = convert(policy);
        policyMongo.setId(policyMongo.getId() == null ? RandomString.generate() : policyMongo.getId());
        return Single.fromPublisher(mongoCollection.insertOne(policyMongo))
                .flatMap(success -> {
                    policy.setId(policyMongo.getId());
                    return Single.just(policy);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PasswordPolicy> update(PasswordPolicy policy) {
        PasswordPolicyMongo policyMongo = convert(policy);
        return Single.fromPublisher(mongoCollection.replaceOne(eq(FIELD_ID, policyMongo.getId()), policyMongo))
                .flatMap(success -> Single.just(policy))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(mongoCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PasswordPolicy> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(mongoCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByReferenceAndId(ReferenceType referenceType, String referenceId, String id) {
        return Maybe.fromPublisher(mongoCollection.find(and(eq(FIELD_ID, id), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByDefaultPolicy(ReferenceType referenceType, String referenceId) {
        return Maybe.fromPublisher(mongoCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_DEFAULT_POLICY, true)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(
                mongoCollection.deleteMany(
                        and(
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_REFERENCE_TYPE, referenceType.name())
                        )
                ))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByOldest(ReferenceType referenceType, String referenceId) {
        return Maybe.fromPublisher(mongoCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()))).sort(ascending(CREATED_AT)).limit(1)).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private PasswordPolicy convert(PasswordPolicyMongo passwordPolicyMongo) {
        var policy = new PasswordPolicy();
        policy.setId(passwordPolicyMongo.getId());
        policy.setReferenceId(passwordPolicyMongo.getReferenceId());
        policy.setReferenceType(ReferenceType.valueOf(passwordPolicyMongo.getReferenceType()));
        policy.setCreatedAt(passwordPolicyMongo.getCreatedAt());
        policy.setUpdatedAt(passwordPolicyMongo.getUpdatedAt());
        policy.setName(passwordPolicyMongo.getName());

        policy.setMinLength(passwordPolicyMongo.getMinLength());
        policy.setMaxConsecutiveLetters(passwordPolicyMongo.getMaxConsecutiveLetters());
        policy.setOldPasswords(passwordPolicyMongo.getOldPasswords());
        policy.setPasswordHistoryEnabled(passwordPolicyMongo.getPasswordHistoryEnabled());
        policy.setLettersInMixedCase(passwordPolicyMongo.getLettersInMixedCase());
        policy.setIncludeSpecialCharacters(passwordPolicyMongo.getIncludeSpecialCharacters());
        policy.setExpiryDuration(passwordPolicyMongo.getExpiryDuration());
        policy.setIncludeNumbers(passwordPolicyMongo.getIncludeNumbers());
        policy.setExcludePasswordsInDictionary(passwordPolicyMongo.getExcludePasswordsInDictionary());
        policy.setExcludeUserProfileInfoInPassword(passwordPolicyMongo.getExcludeUserProfileInfoInPassword());
        policy.setMaxLength(passwordPolicyMongo.getMaxLength());
        policy.setIncludeSpecialCharacters(passwordPolicyMongo.getIncludeSpecialCharacters());
        policy.setDefaultPolicy(passwordPolicyMongo.getDefaultPolicy());

        return policy;
    }

    private PasswordPolicyMongo convert(PasswordPolicy passwordPolicy) {
        var policyMongo = new PasswordPolicyMongo();
        policyMongo.setId(passwordPolicy.getId());
        policyMongo.setReferenceId(passwordPolicy.getReferenceId());
        policyMongo.setReferenceType(passwordPolicy.getReferenceType().name());
        policyMongo.setCreatedAt(passwordPolicy.getCreatedAt());
        policyMongo.setUpdatedAt(passwordPolicy.getUpdatedAt());
        policyMongo.setName(passwordPolicy.getName());

        policyMongo.setMinLength(passwordPolicy.getMinLength());
        policyMongo.setMaxConsecutiveLetters(passwordPolicy.getMaxConsecutiveLetters());
        policyMongo.setOldPasswords(passwordPolicy.getOldPasswords());
        policyMongo.setPasswordHistoryEnabled(passwordPolicy.getPasswordHistoryEnabled());
        policyMongo.setLettersInMixedCase(passwordPolicy.getLettersInMixedCase());
        policyMongo.setIncludeSpecialCharacters(passwordPolicy.getIncludeSpecialCharacters());
        policyMongo.setExpiryDuration(passwordPolicy.getExpiryDuration());
        policyMongo.setIncludeNumbers(passwordPolicy.getIncludeNumbers());
        policyMongo.setExcludePasswordsInDictionary(passwordPolicy.getExcludePasswordsInDictionary());
        policyMongo.setExcludeUserProfileInfoInPassword(passwordPolicy.getExcludeUserProfileInfoInPassword());
        policyMongo.setMaxLength(passwordPolicy.getMaxLength());
        policyMongo.setIncludeSpecialCharacters(passwordPolicy.getIncludeSpecialCharacters());
        policyMongo.setDefaultPolicy(passwordPolicy.getDefaultPolicy());

        return policyMongo;
    }
}
