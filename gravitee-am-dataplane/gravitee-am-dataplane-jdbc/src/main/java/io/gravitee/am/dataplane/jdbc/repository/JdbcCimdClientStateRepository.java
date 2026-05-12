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
import io.gravitee.am.dataplane.api.repository.CimdClientStateRepository;
import io.gravitee.am.dataplane.jdbc.repository.model.JdbcCimdClientState;
import io.gravitee.am.dataplane.jdbc.repository.spring.SpringCimdClientStateRepository;
import io.gravitee.am.model.CimdClientState;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCimdClientStateRepository extends AbstractJdbcRepository implements CimdClientStateRepository {

    private static final String COL_ID = "id";
    private static final String COL_DOMAIN_ID = "domain_id";
    private static final String COL_CLIENT_ID = "client_id";

    @Autowired
    private SpringCimdClientStateRepository springRepo;

    @Override
    public Maybe<CimdClientState> findById(String id) {
        LOGGER.debug("findById({})", id);
        return springRepo.findById(id).map(this::toEntity).observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CimdClientState> findByDomainAndClientId(String domainId, String clientId) {
        LOGGER.debug("findByDomainAndClientId({}, {})", domainId, clientId);
        return springRepo.findByDomainAndClientId(domainId, clientId)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> upsert(CimdClientState state) {
        LOGGER.debug("upsert CimdClientState for domain={}, clientId={}", state.getDomainId(), state.getClientId());
        return springRepo.findByDomainAndClientId(state.getDomainId(), state.getClientId())
                .flatMapSingle(existing -> {
                    state.setId(existing.getId());
                    return update(state);
                })
                .switchIfEmpty(create(state))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> create(CimdClientState item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        item.setUpdatedAt(new Date());
        LOGGER.debug("create CimdClientState with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdClientState> update(CimdClientState item) {
        item.setUpdatedAt(new Date());
        LOGGER.debug("update CimdClientState with id {}", item.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(JdbcCimdClientState.class)
                .matching(Query.query(where(COL_ID).is(id))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("deleteByDomain({})", domainId);
        return monoToCompletable(getTemplate().delete(JdbcCimdClientState.class)
                .matching(Query.query(where(COL_DOMAIN_ID).is(domainId))).all())
                .observeOn(Schedulers.computation());
    }

    private CimdClientState toEntity(JdbcCimdClientState jdbc) {
        if (jdbc == null) return null;
        CimdClientState state = new CimdClientState();
        state.setId(jdbc.getId());
        state.setDomainId(jdbc.getDomainId());
        state.setClientId(jdbc.getClientId());
        state.setMonitoredPropertiesHash(jdbc.getMonitoredPropertiesHash());
        state.setUpdatedAt(toDate(jdbc.getUpdatedAt()));
        return state;
    }

    private JdbcCimdClientState toJdbcEntity(CimdClientState state) {
        if (state == null) return null;
        JdbcCimdClientState jdbc = new JdbcCimdClientState();
        jdbc.setId(state.getId());
        jdbc.setDomainId(state.getDomainId());
        jdbc.setClientId(state.getClientId());
        jdbc.setMonitoredPropertiesHash(state.getMonitoredPropertiesHash());
        jdbc.setUpdatedAt(toLocalDateTime(state.getUpdatedAt()));
        return jdbc;
    }
}
