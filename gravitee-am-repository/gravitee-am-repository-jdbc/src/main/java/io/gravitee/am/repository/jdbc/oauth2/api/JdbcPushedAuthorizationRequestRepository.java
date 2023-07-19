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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcPushedAuthorizationRequest;
import io.gravitee.am.repository.jdbc.oauth2.api.spring.SpringPushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcPushedAuthorizationRequestRepository extends AbstractJdbcRepository implements PushedAuthorizationRequestRepository {

    @Autowired
    private SpringPushedAuthorizationRequestRepository parRepository;

    protected PushedAuthorizationRequest toEntity(JdbcPushedAuthorizationRequest entity) {
        return mapper.map(entity, PushedAuthorizationRequest.class);
    }

    protected JdbcPushedAuthorizationRequest toJdbcEntity(PushedAuthorizationRequest entity) {
        return mapper.map(entity, JdbcPushedAuthorizationRequest.class);
    }

    @Override
    public Maybe<PushedAuthorizationRequest> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return parRepository.findById(id)
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve PushedAuthorizationRequest with id {}", id, error));
    }

    @Override
    public Single<PushedAuthorizationRequest> create(PushedAuthorizationRequest par) {
        par.setId(par.getId() == null ? RandomString.generate() : par.getId());
        LOGGER.debug("Create PushedAuthorizationRequest with id {}", par.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(par))).map(this::toEntity)
                .doOnError((error) -> LOGGER.error("Unable to create PushedAuthorizationRequest with id {}", par.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return parRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete PushedAuthorizationRequest with id {}", id, error));
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcPushedAuthorizationRequest.class)
                .matching(Query.query(where("expire_at")
                        .lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge PushedAuthorizationRequest", error));
    }
}
