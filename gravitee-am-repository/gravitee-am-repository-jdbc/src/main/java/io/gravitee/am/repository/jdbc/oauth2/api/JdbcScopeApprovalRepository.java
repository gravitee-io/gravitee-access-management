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
package io.gravitee.am.repository.jdbc.oauth2.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcScopeApproval;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringScopeApprovalRepository;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcScopeApprovalRepository extends AbstractJdbcRepository implements ScopeApprovalRepository {

    @Autowired
    private SpringScopeApprovalRepository scopeApprovalRepository;

    protected ScopeApproval toEntity(JdbcScopeApproval entity) {
        return mapper.map(entity, ScopeApproval.class);
    }

    protected JdbcScopeApproval toJdbcEntity(ScopeApproval entity) {
        return mapper.map(entity, JdbcScopeApproval.class);
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUserAndClient(String domain, String userId, String clientId) {
        LOGGER.debug("findByDomainAndUserAndClient({}, {}, {})", domain, userId, clientId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return scopeApprovalRepository.findByDomainAndUserAndClient(domain, userId, clientId)
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity);
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUser(String domain, String user) {
        LOGGER.debug("findByDomainAndUser({}, {}, {})", domain, user);
        LocalDateTime now = LocalDateTime.now(UTC);
        return scopeApprovalRepository.findByDomainAndUser(domain, user)
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity);
    }

    @Override
    public Single<ScopeApproval> upsert(ScopeApproval scopeApproval) {
        return scopeApprovalRepository.findByDomainAndUserAndClientAndScope(scopeApproval.getDomain(),
                scopeApproval.getUserId(),
                scopeApproval.getClientId(),
                scopeApproval.getScope())
                .map(this::toEntity)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalApproval -> {
                    if (!optionalApproval.isPresent()) {
                        scopeApproval.setCreatedAt(new Date());
                        scopeApproval.setUpdatedAt(scopeApproval.getCreatedAt());
                        return create(scopeApproval);
                    } else {
                        scopeApproval.setId(optionalApproval.get().getId());
                        scopeApproval.setUpdatedAt(new Date());
                        return update(scopeApproval);
                    }
                });
    }

    @Override
    public Completable deleteByDomainAndScopeKey(String domain, String scope) {
        LOGGER.debug("deleteByDomainAndScopeKey({}, {})", domain, scope);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(where("domain").is(domain)
                        .and(where("scope").is(scope)))).all());
    }

    @Override
    public Completable deleteByDomainAndUserAndClient(String domain, String user, String client) {
        LOGGER.debug("deleteByDomainAndUserAndClient({}, {}, {})", domain, user, client);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(where("domain").is(domain)
                        .and(where("user_id").is(user)
                        .and(where("client_id").is(client))))).all());
    }

    @Override
    public Completable deleteByDomainAndUser(String domain, String user) {
        LOGGER.debug("deleteByDomainAndUser({}, {})", domain, user);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(where("domain").is(domain)
                        .and(where("user_id").is(user)))).all());
    }

    @Override
    public Maybe<ScopeApproval> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return scopeApprovalRepository.findById(id)
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity);
    }

    @Override
    public Single<ScopeApproval> create(ScopeApproval item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create ScopeApproval with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<ScopeApproval> update(ScopeApproval item) {
        LOGGER.debug("Update ScopeApproval with id {}", item.getId());
        return scopeApprovalRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete ScopeApproval with id {}", id);
        return scopeApprovalRepository.deleteById(id);
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class).matching(Query.query(where("expires_at").lessThan(now))).all());
    }
}
