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
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.MembershipMongo;
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

import java.util.HashMap;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoMembershipRepository extends AbstractManagementMongoRepository implements MembershipRepository {

    private static final String FIELD_MEMBER_ID = "memberId";
    private static final String FIELD_MEMBER_TYPE = "memberType";
    private static final String FIELD_ROLE = "role";
    private MongoCollection<MembershipMongo> membershipsCollection;

    @PostConstruct
    public void init() {
        membershipsCollection = mongoOperations.getCollection("memberships", MembershipMongo.class);
        super.init(membershipsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1), new IndexOptions().name("ri1rt1"));
        indexes.put(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_MEMBER_ID, 1), new IndexOptions().name("ri1mi1"));
        indexes.put(new Document(FIELD_MEMBER_ID, 1).append(FIELD_MEMBER_TYPE, 1), new IndexOptions().name("mi1mt1"));

        super.createIndex(membershipsCollection, indexes);
    }

    @Override
    public Flowable<Membership> findByReference(String referenceId, ReferenceType referenceType) {
        return Flowable.fromPublisher(withMaxTime(membershipsCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name())))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Membership> findByMember(String memberId, MemberType memberType) {
        return Flowable.fromPublisher(withMaxTime(membershipsCollection.find(and(eq(FIELD_MEMBER_ID, memberId), eq(FIELD_MEMBER_TYPE, memberType.name())))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Membership> findByCriteria(ReferenceType referenceType, String referenceId, MembershipCriteria criteria) {

        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));
        Bson eqGroupId = null;
        Bson eqUserId = null;

        if (criteria.getGroupIds().isPresent()) {
            eqGroupId = and(eq(FIELD_MEMBER_TYPE, MemberType.GROUP.name()), in(FIELD_MEMBER_ID, criteria.getGroupIds().get()));
        }

        if (criteria.getUserId().isPresent()) {
            eqUserId = and(eq(FIELD_MEMBER_TYPE, MemberType.USER.name()), eq(FIELD_MEMBER_ID, criteria.getUserId().get()));
        }

        if (criteria.getRoleId().isPresent()) {
            eqUserId = eq(FIELD_ROLE, criteria.getRoleId().get());
        }

        return toBsonFilter(criteria.isLogicalOR(), eqGroupId, eqUserId)
                .map(filter -> and(eqReference, filter))
                .switchIfEmpty(Single.just(eqReference))
                .flatMapPublisher(filter -> Flowable.fromPublisher(withMaxTime(membershipsCollection.find(filter)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Membership> findByReferenceAndMember(ReferenceType referenceType, String referenceId, MemberType memberType, String memberId) {
        return Observable.fromPublisher(membershipsCollection.find(
                and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId),
                        eq(FIELD_MEMBER_TYPE, memberType.name()), eq(FIELD_MEMBER_ID, memberId))).first())
                .firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Membership> findById(String id) {
        return Observable.fromPublisher(membershipsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Membership> create(Membership item) {
        MembershipMongo membership = convert(item);
        membership.setId(membership.getId() == null ? RandomString.generate() : membership.getId());
        return Single.fromPublisher(membershipsCollection.insertOne(membership)).map(success -> convert(membership))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Membership> update(Membership item) {
        MembershipMongo membership = convert(item);
        return Single.fromPublisher(membershipsCollection.replaceOne(eq(FIELD_ID, membership.getId()), membership)).map(success -> convert(membership))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(membershipsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Membership convert(MembershipMongo membershipMongo) {
        Membership membership = new Membership();
        membership.setId(membershipMongo.getId());
        membership.setDomain(membershipMongo.getDomain());
        membership.setMemberId(membershipMongo.getMemberId());
        membership.setMemberType(MemberType.valueOf(membershipMongo.getMemberType()));
        membership.setReferenceId(membershipMongo.getReferenceId());
        membership.setReferenceType(ReferenceType.valueOf(membershipMongo.getReferenceType()));
        membership.setRoleId(membershipMongo.getRole());
        membership.setCreatedAt(membershipMongo.getCreatedAt());
        membership.setUpdatedAt(membershipMongo.getUpdatedAt());
        return membership;
    }

    private MembershipMongo convert(Membership membership) {
        MembershipMongo membershipMongo = new MembershipMongo();
        membershipMongo.setId(membership.getId());
        membershipMongo.setDomain(membership.getDomain());
        membershipMongo.setMemberId(membership.getMemberId());
        membershipMongo.setMemberType(membership.getMemberType().name());
        membershipMongo.setReferenceId(membership.getReferenceId());
        membershipMongo.setReferenceType(membership.getReferenceType().name());
        membershipMongo.setRole(membership.getRoleId());
        membershipMongo.setCreatedAt(membership.getCreatedAt());
        membershipMongo.setUpdatedAt(membership.getUpdatedAt());
        return membershipMongo;
    }
}
