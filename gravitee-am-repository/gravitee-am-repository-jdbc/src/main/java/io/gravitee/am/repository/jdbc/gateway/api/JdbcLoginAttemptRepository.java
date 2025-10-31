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
package io.gravitee.am.repository.jdbc.gateway.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.repository.gateway.api.LoginAttemptRepository;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryIllegalQueryException;
import io.gravitee.am.repository.jdbc.gateway.api.model.JdbcLoginAttempt;
import io.gravitee.am.repository.jdbc.gateway.api.spring.SpringLoginAttemptRepository;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcLoginAttemptRepository extends AbstractJdbcRepository implements LoginAttemptRepository {

    public static final String EXPIRE_AT = "expire_at";
    @Autowired
    protected SpringLoginAttemptRepository loginAttemptRepository;

    protected LoginAttempt toEntity(JdbcLoginAttempt entity) {
        return mapper.map(entity, LoginAttempt.class);
    }

    protected JdbcLoginAttempt toJdbcEntity(LoginAttempt entity) {
        return mapper.map(entity, JdbcLoginAttempt.class);
    }

    @Override
    public Maybe<LoginAttempt> findByCriteria(LoginAttemptCriteria criteria) {
        LOGGER.debug("findByCriteria({})", criteria);

        Criteria whereClause = buildWhereClause(criteria);

        whereClause = whereClause.and(
                where(EXPIRE_AT).greaterThan(LocalDateTime.now(UTC))
                .or(where(EXPIRE_AT).isNull()));

        return monoToMaybe(getTemplate().select(Query.query(whereClause).with(PageRequest.of(0,1, Sort.by("id"))), JdbcLoginAttempt.class).singleOrEmpty())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    private Criteria buildWhereClause(LoginAttemptCriteria criteria) {
        Criteria whereClause = Criteria.empty();
        // domain
        if (criteria.domain() != null && !criteria.domain().isEmpty()) {
            whereClause = whereClause.and(where("domain").is(criteria.domain()));
        }
        // client
        if (criteria.client() != null && !criteria.client().isEmpty()) {
            whereClause = whereClause.and(where("client").is(criteria.client()));
        }
        // idp
        if (criteria.identityProvider() != null && !criteria.identityProvider().isEmpty()) {
            whereClause = whereClause.and(where("identity_provider").is(criteria.identityProvider()));
        }
        // username
        if (criteria.username() != null && !criteria.username().isEmpty()) {
            whereClause = whereClause.and(where("username").is(criteria.username()));
        }
        return whereClause;
    }

    @Override
    public Completable delete(LoginAttemptCriteria criteria) {
        LOGGER.debug("delete({})", criteria);

        Criteria whereClause = buildWhereClause(criteria);

        if (!whereClause.isEmpty()) {
            return monoToCompletable(getTemplate().delete(JdbcLoginAttempt.class).matching(Query.query(whereClause)).all())
                    .observeOn(Schedulers.computation());
        }

        throw new RepositoryIllegalQueryException("Unable to delete from LoginAttempt without criteria");
    }

    @Override
    public Maybe<LoginAttempt> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return loginAttemptRepository.findById(id)
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<LoginAttempt> create(LoginAttempt item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create LoginAttempt with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<LoginAttempt> update(LoginAttempt item) {
        LOGGER.debug("update loginAttempt with id '{}'", item.getId());
        return loginAttemptRepository.save(toJdbcEntity(item))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return loginAttemptRepository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcLoginAttempt.class).matching(Query.query(where(EXPIRE_AT).lessThan(now))).all())
                .observeOn(Schedulers.computation());
    }
}
