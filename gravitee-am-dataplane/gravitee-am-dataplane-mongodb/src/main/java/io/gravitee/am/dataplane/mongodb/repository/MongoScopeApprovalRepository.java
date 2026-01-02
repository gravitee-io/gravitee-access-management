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
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.mongodb.repository.model.ScopeApprovalMongo;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.DEFAULT_USER_FIELDS;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_USER_EXTERNAL_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_USER_SOURCE;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.dropIndexes;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.userIdMatches;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoScopeApprovalRepository extends AbstractDataPlaneMongoRepository implements ScopeApprovalRepository {

    private static final String FIELD_TRANSACTION_ID = "transactionId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_SCOPE = "scope";
    private MongoCollection<ScopeApprovalMongo> scopeApprovalsCollection;

    private final Set<String> UNUSED_INDEXES = Set.of("d1c1u1");


    @PostConstruct
    public void init() {
        scopeApprovalsCollection = mongoDatabase.getCollection("scope_approvals", ScopeApprovalMongo.class);
        MongoUtils.init(scopeApprovalsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_TRANSACTION_ID, 1), new IndexOptions().name("t1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("d1u1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1).append(FIELD_USER_ID, 1).append(FIELD_SCOPE, 1), new IndexOptions().name("d1c1u1s1"));
        indexes.put(new Document(FIELD_DOMAIN, 1)
                .append(FIELD_USER_EXTERNAL_ID, 1)
                .append(FIELD_USER_SOURCE, 1), new IndexOptions().name("d1ue1us1"));
        indexes.put(new Document(FIELD_DOMAIN, 1)
                .append(FIELD_CLIENT_ID, 1)
                .append(FIELD_USER_EXTERNAL_ID, 1)
                .append(FIELD_USER_SOURCE, 1)
                .append(FIELD_SCOPE, 1), new IndexOptions().name("d1c1ue1us1s1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRES_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));

        super.createIndex(scopeApprovalsCollection, indexes);
        if (getEnsureIndexOnStart()) {
            dropIndexes(scopeApprovalsCollection, UNUSED_INDEXES::contains).subscribe();
        }
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUserAndClient(String domain, UserId userId, String clientId) {
        return Flowable.fromPublisher(scopeApprovalsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, clientId), userIdMatches(userId, DEFAULT_USER_FIELDS), gte(FIELD_EXPIRES_AT, new Date())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUser(String domain, UserId userId) {
        return Flowable.fromPublisher(scopeApprovalsCollection.find(and(eq(FIELD_DOMAIN, domain), userIdMatches(userId, DEFAULT_USER_FIELDS), gte(FIELD_EXPIRES_AT, new Date())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<ScopeApproval> findById(String id) {
        return Observable.fromPublisher(scopeApprovalsCollection.find(and(eq(FIELD_ID, id), gte(FIELD_EXPIRES_AT, new Date()))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> create(ScopeApproval scopeApproval) {
        ScopeApprovalMongo scopeApprovalMongo = convert(scopeApproval);
        scopeApprovalMongo.setId(scopeApprovalMongo.getId() == null ? RandomString.generate() : scopeApprovalMongo.getId());
        return Single.fromPublisher(scopeApprovalsCollection.insertOne(scopeApprovalMongo)).flatMap(success -> Single.just(scopeApproval))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> update(ScopeApproval scopeApproval) {
        ScopeApprovalMongo scopeApprovalMongo = convert(scopeApproval);

        return Single.fromPublisher(scopeApprovalsCollection.replaceOne(
                and(eq(FIELD_DOMAIN, scopeApproval.getDomain()),
                        eq(FIELD_CLIENT_ID, scopeApproval.getClientId()),
                        eq(FIELD_USER_ID, scopeApproval.getUserId()),
                        eq(FIELD_SCOPE, scopeApproval.getScope())),
                scopeApprovalMongo)).flatMap(updateResult -> Single.just(scopeApproval))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> upsert(ScopeApproval scopeApproval) {
        return Observable.fromPublisher(scopeApprovalsCollection.find(
                        and(eq(FIELD_DOMAIN, scopeApproval.getDomain()),
                                eq(FIELD_CLIENT_ID, scopeApproval.getClientId()),
                                userIdMatches(scopeApproval.getUserId(), DEFAULT_USER_FIELDS),
                                eq(FIELD_SCOPE, scopeApproval.getScope()))).first())
                .firstElement()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalApproval -> {
                    if (optionalApproval.isEmpty()) {
                        scopeApproval.setCreatedAt(new Date());
                        scopeApproval.setUpdatedAt(scopeApproval.getCreatedAt());
                        return create(scopeApproval);
                    } else {
                        scopeApproval.setId(optionalApproval.get().getId());
                        scopeApproval.setUpdatedAt(new Date());
                        return update(scopeApproval);
                    }
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndScopeKey(String domain, String scope) {
        return Completable.fromPublisher(scopeApprovalsCollection.deleteMany(
                and(eq(FIELD_DOMAIN, domain), eq(FIELD_SCOPE, scope))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(scopeApprovalsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndUserAndClient(String domain, UserId userId, String client) {
        return Completable.fromPublisher(scopeApprovalsCollection.deleteMany(
                and(eq(FIELD_DOMAIN, domain), userIdMatches(userId, DEFAULT_USER_FIELDS), eq(FIELD_CLIENT_ID, client))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndUser(String domain, UserId userId) {
        return Completable.fromPublisher(scopeApprovalsCollection.deleteMany(
                and(eq(FIELD_DOMAIN, domain), userIdMatches(userId, DEFAULT_USER_FIELDS))))
                .observeOn(Schedulers.computation());
    }

    private ScopeApproval convert(ScopeApprovalMongo scopeApprovalMongo) {
        if (scopeApprovalMongo == null) {
            return null;
        }

        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId(scopeApprovalMongo.getId());
        scopeApproval.setTransactionId(scopeApprovalMongo.getTransactionId());
        scopeApproval.setClientId(scopeApprovalMongo.getClientId());
        scopeApproval.setUserId(new UserId(scopeApprovalMongo.getUserId(), scopeApprovalMongo.getUserExternalId(), scopeApprovalMongo.getUserSource()));
        scopeApproval.setScope(scopeApprovalMongo.getScope());
        scopeApproval.setExpiresAt(scopeApprovalMongo.getExpiresAt());
        scopeApproval.setStatus(ScopeApproval.ApprovalStatus.valueOf(scopeApprovalMongo.getStatus().toUpperCase()));
        scopeApproval.setDomain(scopeApprovalMongo.getDomain());
        scopeApproval.setCreatedAt(scopeApprovalMongo.getCreatedAt());
        scopeApproval.setUpdatedAt(scopeApprovalMongo.getUpdatedAt());

        return scopeApproval;
    }

    private ScopeApprovalMongo convert(ScopeApproval scopeApproval) {
        if (scopeApproval == null) {
            return null;
        }

        ScopeApprovalMongo scopeApprovalMongo = new ScopeApprovalMongo();
        scopeApprovalMongo.setId(scopeApproval.getId());
        scopeApprovalMongo.setTransactionId(scopeApproval.getTransactionId());
        scopeApprovalMongo.setClientId(scopeApproval.getClientId());
        scopeApprovalMongo.setUserId(scopeApproval.getUserId().id());
        scopeApprovalMongo.setUserExternalId(scopeApproval.getUserId().externalId());
        scopeApprovalMongo.setUserSource(scopeApproval.getUserId().source());
        scopeApprovalMongo.setScope(scopeApproval.getScope());
        scopeApprovalMongo.setExpiresAt(scopeApproval.getExpiresAt());
        scopeApprovalMongo.setStatus(scopeApproval.getStatus().name().toUpperCase());
        scopeApprovalMongo.setDomain(scopeApproval.getDomain());
        scopeApprovalMongo.setCreatedAt(scopeApproval.getCreatedAt());
        scopeApprovalMongo.setUpdatedAt(scopeApproval.getUpdatedAt());

        return scopeApprovalMongo;
    }
}
