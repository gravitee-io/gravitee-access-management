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
package io.gravitee.am.repository.jdbc.oauth2.oidc;

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.oidc.model.JdbcCibaAuthRequest;
import io.gravitee.am.repository.jdbc.oauth2.oidc.model.JdbcRequestObject;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCibaAuthReqRepository extends AbstractJdbcRepository implements CibaAuthRequestRepository {

    protected CibaAuthRequest toEntity(JdbcCibaAuthRequest entity) {
        return mapper.map(entity, CibaAuthRequest.class);
    }

    protected JdbcCibaAuthRequest toJdbcEntity(CibaAuthRequest entity) {
        return mapper.map(entity, JdbcCibaAuthRequest.class);
    }

    @Override
    public Maybe<CibaAuthRequest> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("id").is(id)), JdbcCibaAuthRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity);
    }

    @Override
    public Maybe<CibaAuthRequest> findByExternalId(String externalId) {
        LOGGER.debug("findByExternalId({})", externalId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("ext_transaction_id").is(externalId)), JdbcCibaAuthRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity);
    }

    @Override
    public Single<CibaAuthRequest> create(CibaAuthRequest authreq) {
        authreq.setId(authreq.getId() == null ? SecureRandomString.generate() : authreq.getId());
        LOGGER.debug("Create CibaAuthRequest with id {}", authreq.getId());
        return monoToSingle(getTemplate().insert(this.toJdbcEntity(authreq))).map(this::toEntity);
    }

    @Override
    public Single<CibaAuthRequest> update(CibaAuthRequest authreq) {
        LOGGER.debug("Update CibaAuthRequest with id {}", authreq.getId());
        return monoToSingle(getTemplate().update(this.toJdbcEntity(authreq))).map(this::toEntity);
    }

    @Override
    public Single<CibaAuthRequest> updateStatus(String authReqId, String status) {
        LOGGER.debug("Update CibaAuthRequest {} with status {}", authReqId, status);
        final Mono<Integer> action = getTemplate().update(
                Query.query(where("id").is(authReqId)),
                Update.update("status", status),
                JdbcCibaAuthRequest.class);
        return monoToSingle(action).flatMap((i) -> findById(authReqId).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(Query.query(where("id").is(id)), JdbcCibaAuthRequest.class));
    }

    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(Query.query(where("expire_at").lessThan(now)), JdbcRequestObject.class)).doOnError(error -> LOGGER.error("Unable to purge CibaAuthRequests", error));
    }
}
