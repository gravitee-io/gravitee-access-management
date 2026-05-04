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
package io.gravitee.am.repository.jdbc.oauth2.aauth;

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.aauth.model.JdbcAAuthBootstrapRequest;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * JDBC repository for AAUTH bootstrap requests.
 * Follows the same pattern as {@link JdbcAAuthPendingRequestRepository}.
 *
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAAuthBootstrapRequestRepository extends AbstractJdbcRepository implements AAuthBootstrapRequestRepository {

    protected AAuthBootstrapRequest toEntity(JdbcAAuthBootstrapRequest entity) {
        return mapper.map(entity, AAuthBootstrapRequest.class);
    }

    protected JdbcAAuthBootstrapRequest toJdbcEntity(AAuthBootstrapRequest entity) {
        return mapper.map(entity, JdbcAAuthBootstrapRequest.class);
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("id").is(id)), JdbcAAuthBootstrapRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findByInteractionCode(String code) {
        LOGGER.debug("findByInteractionCode({})", code);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("interaction_code").is(code)), JdbcAAuthBootstrapRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findByEphemeralKeyThumbprint(String thumbprint) {
        LOGGER.debug("findByEphemeralKeyThumbprint({})", thumbprint);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("ephemeral_key_thumbprint").is(thumbprint)), JdbcAAuthBootstrapRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> create(AAuthBootstrapRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        LOGGER.debug("Create AAuthBootstrapRequest with id {}", request.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> update(AAuthBootstrapRequest request) {
        LOGGER.debug("Update AAuthBootstrapRequest with id {}", request.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> updateStatus(String id, String status) {
        LOGGER.debug("Update AAuthBootstrapRequest {} with status {}", id, status);
        return monoToSingle(getTemplate().update(
                Query.query(where("id").is(id)),
                Update.update("status", status),
                JdbcAAuthBootstrapRequest.class))
                .flatMap(i -> findById(id).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(Query.query(where("id").is(id)), JdbcAAuthBootstrapRequest.class))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(Query.query(where("expire_at").lessThan(now)), JdbcAAuthBootstrapRequest.class))
                .doOnError(error -> LOGGER.error("Unable to purge AAuthBootstrapRequests", error))
                .observeOn(Schedulers.computation());
    }
}
