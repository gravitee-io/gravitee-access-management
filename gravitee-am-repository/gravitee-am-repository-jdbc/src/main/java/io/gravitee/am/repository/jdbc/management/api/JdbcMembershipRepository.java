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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcMembership;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringMembershipRepository;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcMembershipRepository extends AbstractJdbcRepository implements MembershipRepository {
    @Autowired
    private SpringMembershipRepository membershipRepository;

    protected Membership toEntity(JdbcMembership entity) {
        return mapper.map(entity, Membership.class);
    }

    protected JdbcMembership toJdbcEntity(Membership entity) {
        return mapper.map(entity, JdbcMembership.class);
    }

    @Override
    public Flowable<Membership> findByReference(String referenceId, ReferenceType referenceType) {
        LOGGER.debug("findByReference({},{})", referenceId, referenceType);
        return this.membershipRepository.findByReference(referenceId, referenceType.name())
                .map(this::toEntity);
    }

    @Override
    public Flowable<Membership> findByMember(String memberId, MemberType memberType) {
        LOGGER.debug("findByMember({},{})", memberId, memberType);
        return this.membershipRepository.findByMember(memberId, memberType.name())
                .map(this::toEntity);
    }

    @Override
    public Flowable<Membership> findByCriteria(ReferenceType referenceType, String referenceId, MembershipCriteria criteria) {
        LOGGER.debug("findByCriteria({},{},{}", referenceType, referenceId, criteria);
        Criteria whereClause = Criteria.empty();
        Criteria groupClause = Criteria.empty();
        Criteria userClause = Criteria.empty();

        Criteria referenceClause = where("reference_id").is(referenceId).and(where("reference_type").is(referenceType.name()));

        if (criteria.getGroupIds().isPresent()) {
            groupClause = where("member_id").in(criteria.getGroupIds().get()).and(where("member_type").is(MemberType.GROUP.name()));
        }

        if (criteria.getUserId().isPresent()) {
            userClause = where("member_id").is(criteria.getUserId().get()).and(where("member_type").is(MemberType.USER.name()));
        }

        if (criteria.getRoleId().isPresent()) {
            userClause = where("role_id").is(criteria.getRoleId().get());
        }

        whereClause = whereClause.and(referenceClause.and(criteria.isLogicalOR() ? userClause.or(groupClause) : userClause.and(groupClause)));

        return fluxToFlowable(getTemplate().select(Query.query(whereClause), JdbcMembership.class))
                .map(this::toEntity);
    }

    @Override
    public Maybe<Membership> findByReferenceAndMember(ReferenceType referenceType, String referenceId, MemberType memberType, String memberId) {
        LOGGER.debug("findByReferenceAndMember({},{},{},{})", referenceType,referenceId,memberType,memberId);
        return this.membershipRepository.findByReferenceAndMember(referenceId, referenceType.name(), memberId, memberType.name())
                .map(this::toEntity);
    }

    @Override
    public Maybe<Membership> findById(String id) {
        LOGGER.debug("findById({})", id);
        return membershipRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Membership> create(Membership item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Membership with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<Membership> update(Membership item) {
        LOGGER.debug("update membership with id {}", item.getId());
        return membershipRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return membershipRepository.deleteById(id);
    }
}
