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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.MembershipMongo;
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoMembershipRepository extends AbstractManagementMongoRepository implements MembershipRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_REFERENCE_ID = "referenceId";
    private static final String FIELD_REFERENCE_TYPE = "referenceType";
    private static final String FIELD_MEMBER_ID = "memberId";
    private MongoCollection<MembershipMongo> membershipsCollection;

    @PostConstruct
    public void init() {
        membershipsCollection = mongoOperations.getCollection("memberships", MembershipMongo.class);
        membershipsCollection.createIndex(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_REFERENCE_TYPE, 1)).subscribe(new LoggableIndexSubscriber());
        membershipsCollection.createIndex(new Document(FIELD_REFERENCE_ID, 1).append(FIELD_MEMBER_ID, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Flowable<Membership> findAll() {
        return Flowable.fromPublisher(membershipsCollection.find()).map(this::convert);
    }

    @Override
    public Single<List<Membership>> findByReference(String referenceId, ReferenceType referenceType) {
        return Observable.fromPublisher(membershipsCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_REFERENCE_TYPE, referenceType.name()))))
                .map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<Membership> findByReferenceAndMember(String referenceId, String memberId) {
        return Observable.fromPublisher(membershipsCollection.find(and(eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_MEMBER_ID, memberId))).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<Membership> findById(String id) {
        return Observable.fromPublisher(membershipsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Membership> create(Membership item) {
        MembershipMongo membership = convert(item);
        membership.setId(membership.getId() == null ? RandomString.generate() : membership.getId());
        return Single.fromPublisher(membershipsCollection.insertOne(membership)).map(success -> convert(membership));
    }

    @Override
    public Single<Membership> update(Membership item) {
        MembershipMongo membership = convert(item);
        return Single.fromPublisher(membershipsCollection.replaceOne(eq(FIELD_ID, membership.getId()), membership)).map(success -> convert(membership));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(membershipsCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private Membership convert(MembershipMongo membershipMongo) {
        Membership membership = new Membership();
        membership.setId(membershipMongo.getId());
        membership.setDomain(membershipMongo.getDomain());
        membership.setMemberId(membershipMongo.getMemberId());
        membership.setMemberType(MemberType.valueOf(membershipMongo.getMemberType()));
        membership.setReferenceId(membershipMongo.getReferenceId());
        membership.setReferenceType(ReferenceType.valueOf(membershipMongo.getReferenceType()));
        membership.setRole(membershipMongo.getRole());
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
        membershipMongo.setRole(membership.getRole());
        membershipMongo.setCreatedAt(membership.getCreatedAt());
        membershipMongo.setUpdatedAt(membership.getUpdatedAt());
        return membershipMongo;
    }
}
