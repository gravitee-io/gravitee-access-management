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
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryIllegalQueryException;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcRateLimit;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringRateLimitRepository;
import io.gravitee.am.repository.management.api.RateLimitRepository;
import io.gravitee.am.repository.management.api.search.RateLimitCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcRateLimitRepository extends AbstractJdbcRepository implements RateLimitRepository {

    @Autowired
    protected SpringRateLimitRepository rateLimitRepository;

    protected RateLimit toEntity(JdbcRateLimit entity) { return mapper.map(entity, RateLimit.class);}
    protected JdbcRateLimit toJdbcEntity(RateLimit entity) {return mapper.map(entity, JdbcRateLimit.class);}

    @Override
    public Maybe<RateLimit> findById(String id) {
        LOGGER.debug("RateLimit findById({})", id);
        return rateLimitRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Maybe<RateLimit> findByCriteria(RateLimitCriteria criteria) {
        Criteria whereClause = buildWhereClause(criteria);
        return monoToMaybe(getTemplate().select(Query.query(whereClause).with(PageRequest.of(0,1, Sort.by("id"))), JdbcRateLimit.class)
                .singleOrEmpty()).map(this::toEntity);
    }

    @Override
    public Single<RateLimit> create(RateLimit item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create RateLimit with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<RateLimit> update(RateLimit item) {
        LOGGER.debug("update RateLimit with id '{}'", item.getId());
        return rateLimitRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete RateLimit with id '{}'", id);
        return rateLimitRepository.deleteById(id);
    }

    @Override
    public Completable delete(RateLimitCriteria criteria) {
        LOGGER.debug("delete RateLimit with id '{}'", criteria);
        Criteria whereClause = buildWhereClause(criteria);

        if (!whereClause.isEmpty()) {
            return monoToCompletable(getTemplate().delete(JdbcRateLimit.class).matching(Query.query(whereClause)).all());
        }

        throw new RepositoryIllegalQueryException("Unable to delete from RateLimit with criteria");
    }

    @Override
    public Completable deleteByUser(String userId) {
        LOGGER.debug("delete RateLimit with user id '{}'", userId);

        Criteria whereClause = Criteria.empty();
        if(userId != null && !userId.isEmpty()){
            whereClause = whereClause.and(where("user_id").is(userId));
        }

        if (!whereClause.isEmpty()) {
            return monoToCompletable(getTemplate().delete(JdbcRateLimit.class).matching(Query.query(whereClause)).all());
        }

        throw new RepositoryIllegalQueryException("Unable to delete from RateLimit with userId");
    }

    @Override
    public Completable deleteByDomain(String domainId, ReferenceType referenceType) {
        LOGGER.debug("delete RateLimit with domain id '{}' and reference type {}", domainId, referenceType);

        Criteria whereClause = Criteria.empty();
        if(domainId != null && !domainId.isEmpty()){
            whereClause = whereClause.and(where("reference_id").is(domainId)).and(where("reference_type").is(referenceType));
        }

        if (!whereClause.isEmpty()) {
            return monoToCompletable(getTemplate().delete(JdbcRateLimit.class).matching(Query.query(whereClause)).all());
        }

        throw new RepositoryIllegalQueryException("Unable to delete from RateLimit with domainId");
    }

    protected Criteria buildWhereClause(RateLimitCriteria criteria){
        Criteria whereClause = Criteria.empty();

        if(criteria.userId() != null && !criteria.userId().isEmpty()){
            whereClause = whereClause.and(where("user_id").is(criteria.userId()));
        }

        if(criteria.factorId() != null && !criteria.factorId().isEmpty()){
            whereClause = whereClause.and(where("factor_id").is(criteria.factorId()));
        }

        if(criteria.client() != null && !criteria.client().isEmpty()){
            whereClause = whereClause.and(where("client").is(criteria.client()));
        }

        return whereClause;
    }
}
