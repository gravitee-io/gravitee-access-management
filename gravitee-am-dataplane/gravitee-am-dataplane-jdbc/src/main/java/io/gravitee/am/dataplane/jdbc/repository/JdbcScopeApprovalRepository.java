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
package io.gravitee.am.dataplane.jdbc.repository;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.jdbc.repository.spring.SpringScopeApprovalRepository;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.dataplane.jdbc.repository.model.JdbcScopeApproval;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
@Slf4j
public class JdbcScopeApprovalRepository extends AbstractJdbcRepository implements ScopeApprovalRepository {

    public static final String DOMAIN = "domain";
    @Autowired
    private SpringScopeApprovalRepository scopeApprovalRepository;

    protected ScopeApproval toEntity(JdbcScopeApproval dbEntity) {
        return dbEntity.toEntity();
    }

    protected JdbcScopeApproval toJdbcEntity(ScopeApproval entity) {
        return JdbcScopeApproval.of(entity);
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUserAndClient(String domain, UserId userId, String clientId) {
        LOGGER.debug("findByDomainAndUserAndClient({}, {}, {})", domain, userId, clientId);
        LocalDateTime now = LocalDateTime.now(UTC);
            return findAll(Query.query(userIdMatches(userId)
                .and(client(clientId))
                .and(domain(domain))))
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity)
                    .observeOn(Schedulers.computation());
    }

    private Flowable<JdbcScopeApproval> findAll(Query query) {
        return fluxToFlowable(getTemplate().select(JdbcScopeApproval.class)
                .matching(query).all());
    }

    private Criteria client(String clientId) {
        return where("client_id").is(clientId);
    }

    private Criteria scope(String scope) {
        return where("scope").is(scope);
    }

    private Criteria domain(String domain) {
        return where("domain").is(domain);
    }

    @Override
    public Flowable<ScopeApproval> findByDomainAndUser(String domain, UserId userId) {
        LOGGER.debug("findByDomainAndUser({}, {})", domain, userId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return findAll(Query.query(userIdMatches(userId).and(domain(domain))))
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> upsert(ScopeApproval scopeApproval) {
        return monoToMaybe(getTemplate().select(JdbcScopeApproval.class)
                .matching(Query.query(userIdMatches(scopeApproval.getUserId())
                        .and(client(scopeApproval.getClientId())
                                .and(scope(scopeApproval.getScope()))
                                .and(domain(scopeApproval.getDomain())))
                )).first())
                .map(this::toEntity)
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
        LOGGER.debug("deleteByDomainAndScopeKey({}, {})", domain, scope);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(where(DOMAIN).is(domain)
                        .and(where("scope").is(scope)))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndUserAndClient(String domain, UserId userId, String client) {
        LOGGER.debug("deleteByDomainAndUserAndClient({}, {}, {})", domain, userId, client);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(domain(domain)
                        .and(userIdMatches(userId))
                        .and(client(client))))
                .all()
                .doOnNext(rows -> log.warn("deleteByDomainAndUserAndClient({},{},{}): {} deleted", domain, userId, client, rows)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndUser(String domain, UserId userId) {
        LOGGER.debug("deleteByDomainAndUser({}, {})", domain, userId);

        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class)
                .matching(Query.query(domain(domain)
                        .and(userIdMatches(userId))))
                .all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<ScopeApproval> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return scopeApprovalRepository.findById(id)
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> create(ScopeApproval item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create ScopeApproval with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<ScopeApproval> update(ScopeApproval item) {
        LOGGER.debug("Update ScopeApproval with id {}", item.getId());
        return scopeApprovalRepository.save(toJdbcEntity(item))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete ScopeApproval with id {}", id);
        return scopeApprovalRepository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcScopeApproval.class).matching(Query.query(where("expires_at").lessThan(now))).all())
                .observeOn(Schedulers.computation());
    }
}
