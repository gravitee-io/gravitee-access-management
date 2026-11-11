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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.AuthorizationDetailApprovalRepository;
import io.gravitee.am.dataplane.mongodb.repository.model.AuthorizationDetailApprovalMongo;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
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
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.DEFAULT_USER_FIELDS;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.userIdMatches;

@Component
public class MongoAuthorizationDetailApprovalRepository extends AbstractDataPlaneMongoRepository implements AuthorizationDetailApprovalRepository {

    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private MongoCollection<AuthorizationDetailApprovalMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoDatabase.getCollection("authorization_detail_approvals", AuthorizationDetailApprovalMongo.class);
        MongoUtils.init(collection);
    }

    @Override
    public Flowable<AuthorizationDetailApproval> findByDomainAndUserAndClient(String domain, UserId userId, String clientId) {
        return Flowable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, clientId),
                        userIdMatches(userId, DEFAULT_USER_FIELDS), gte(FIELD_EXPIRES_AT, new Date()))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationDetailApproval> findByDomainAndUser(String domain, UserId userId) {
        return Flowable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain),
                        userIdMatches(userId, DEFAULT_USER_FIELDS), gte(FIELD_EXPIRES_AT, new Date()))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDetailApproval> findById(String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_ID, id), gte(FIELD_EXPIRES_AT, new Date()))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailApproval> create(AuthorizationDetailApproval approval) {
        AuthorizationDetailApprovalMongo approvalMongo = convert(approval);
        approvalMongo.setId(approvalMongo.getId() == null ? RandomString.generate() : approvalMongo.getId());
        approval.setId(approvalMongo.getId());
        return Single.fromPublisher(collection.insertOne(approvalMongo))
                .map(success -> approval)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailApproval> update(AuthorizationDetailApproval approval) {
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, approval.getId()), convert(approval)))
                .map(updateResult -> approval)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailApproval> upsert(AuthorizationDetailApproval approval) {
        return Observable.fromPublisher(collection.find(sameApproval(approval)).first())
                .firstElement()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        approval.setCreatedAt(new Date());
                        approval.setUpdatedAt(approval.getCreatedAt());
                        return create(approval);
                    }
                    approval.setId(existing.get().getId());
                    approval.setCreatedAt(existing.get().getCreatedAt());
                    approval.setUpdatedAt(new Date());
                    return update(approval);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domain) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN, domain)))
                .observeOn(Schedulers.computation());
    }

    private static Bson sameApproval(AuthorizationDetailApproval approval) {
        return and(eq(FIELD_DOMAIN, approval.getDomain()),
                eq(FIELD_CLIENT_ID, approval.getClientId()),
                userIdMatches(approval.getUserId(), DEFAULT_USER_FIELDS),
                eq(FIELD_DIGEST, approval.getDigest()));
    }

    private AuthorizationDetailApproval convert(AuthorizationDetailApprovalMongo approvalMongo) {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setId(approvalMongo.getId());
        approval.setUserId(new UserId(approvalMongo.getUserId(), approvalMongo.getUserExternalId(), approvalMongo.getUserSource()));
        approval.setClientId(approvalMongo.getClientId());
        approval.setDomain(approvalMongo.getDomain());
        approval.setDetail(approvalMongo.getDetail());
        approval.setDigest(approvalMongo.getDigest());
        approval.setStatus(approvalMongo.getStatus() == null ? null : ScopeApproval.ApprovalStatus.valueOf(approvalMongo.getStatus()));
        approval.setExpiresAt(approvalMongo.getExpiresAt());
        approval.setCreatedAt(approvalMongo.getCreatedAt());
        approval.setUpdatedAt(approvalMongo.getUpdatedAt());
        return approval;
    }

    private AuthorizationDetailApprovalMongo convert(AuthorizationDetailApproval approval) {
        AuthorizationDetailApprovalMongo approvalMongo = new AuthorizationDetailApprovalMongo();
        approvalMongo.setId(approval.getId());
        approvalMongo.setUserId(approval.getUserId().id());
        approvalMongo.setUserExternalId(approval.getUserId().externalId());
        approvalMongo.setUserSource(approval.getUserId().source());
        approvalMongo.setClientId(approval.getClientId());
        approvalMongo.setDomain(approval.getDomain());
        approvalMongo.setDetail(approval.getDetail() == null ? null : new Document(approval.getDetail()));
        approvalMongo.setDigest(approval.getDigest());
        approvalMongo.setStatus(approval.getStatus() == null ? null : approval.getStatus().name());
        approvalMongo.setExpiresAt(approval.getExpiresAt());
        approvalMongo.setCreatedAt(approval.getCreatedAt());
        approvalMongo.setUpdatedAt(approval.getUpdatedAt());
        return approvalMongo;
    }
}
