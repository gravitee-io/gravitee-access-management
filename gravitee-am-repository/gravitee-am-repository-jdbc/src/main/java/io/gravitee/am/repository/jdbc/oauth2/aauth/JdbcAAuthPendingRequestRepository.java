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
import io.gravitee.am.repository.jdbc.oauth2.aauth.model.JdbcAAuthPendingRequest;
import io.gravitee.am.repository.oidc.api.AAuthPendingRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
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
 * JDBC repository for AAUTH pending requests.
 * Follows the same pattern as {@link io.gravitee.am.repository.jdbc.oauth2.oidc.JdbcCibaAuthReqRepository}.
 *
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAAuthPendingRequestRepository extends AbstractJdbcRepository implements AAuthPendingRequestRepository {

    protected AAuthPendingRequest toEntity(JdbcAAuthPendingRequest entity) {
        return mapper.map(entity, AAuthPendingRequest.class);
    }

    protected JdbcAAuthPendingRequest toJdbcEntity(AAuthPendingRequest entity) {
        return mapper.map(entity, JdbcAAuthPendingRequest.class);
    }

    @Override
    public Maybe<AAuthPendingRequest> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("id").is(id)), JdbcAAuthPendingRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthPendingRequest> findByInteractionCode(String interactionCode) {
        LOGGER.debug("findByInteractionCode({})", interactionCode);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("interaction_code").is(interactionCode)), JdbcAAuthPendingRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> create(AAuthPendingRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        LOGGER.debug("Create AAuthPendingRequest with id {}", request.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> update(AAuthPendingRequest request) {
        LOGGER.debug("Update AAuthPendingRequest with id {}", request.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> updateStatus(String id, String status) {
        LOGGER.debug("Update AAuthPendingRequest {} with status {}", id, status);
        return monoToSingle(getTemplate().update(
                Query.query(where("id").is(id)),
                Update.update("status", status),
                JdbcAAuthPendingRequest.class))
                .flatMap(i -> findById(id).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(Query.query(where("id").is(id)), JdbcAAuthPendingRequest.class))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(Query.query(where("expire_at").lessThan(now)), JdbcAAuthPendingRequest.class))
                .doOnError(error -> LOGGER.error("Unable to purge AAuthPendingRequests", error))
                .observeOn(Schedulers.computation());
    }
}
