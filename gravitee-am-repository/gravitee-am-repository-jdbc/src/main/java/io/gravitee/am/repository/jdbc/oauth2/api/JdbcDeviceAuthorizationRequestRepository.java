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

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcDeviceAuthorizationRequest;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcDeviceAuthorizationRequestRepository extends AbstractJdbcRepository implements DeviceAuthorizationRequestRepository {

    protected DeviceAuthorizationRequest toEntity(JdbcDeviceAuthorizationRequest entity) {
        return mapper.map(entity, DeviceAuthorizationRequest.class);
    }

    protected JdbcDeviceAuthorizationRequest toJdbcEntity(DeviceAuthorizationRequest entity) {
        return mapper.map(entity, JdbcDeviceAuthorizationRequest.class);
    }

    @Override
    public Maybe<DeviceAuthorizationRequest> findById(String deviceCode) {
        LOGGER.debug("findById({})", deviceCode);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("id").is(deviceCode)), JdbcDeviceAuthorizationRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<DeviceAuthorizationRequest> findByUserCode(String userCode) {
        LOGGER.debug("findByUserCode({})", userCode);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(Query.query(where("user_code").is(userCode)), JdbcDeviceAuthorizationRequest.class).singleOrEmpty())
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> create(DeviceAuthorizationRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        LOGGER.debug("Create DeviceAuthorizationRequest with id {}", request.getId());
        return monoToSingle(getTemplate().insert(this.toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> update(DeviceAuthorizationRequest request) {
        LOGGER.debug("Update DeviceAuthorizationRequest with id {}", request.getId());
        return monoToSingle(getTemplate().update(this.toJdbcEntity(request))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DeviceAuthorizationRequest> updateStatus(String deviceCode, String status) {
        LOGGER.debug("Update DeviceAuthorizationRequest {} with status {}", deviceCode, status);
        final Mono<Long> action = getTemplate().update(
                Query.query(where("id").is(deviceCode)),
                Update.update("status", status),
                JdbcDeviceAuthorizationRequest.class);
        return monoToSingle(action).flatMap(i -> findById(deviceCode).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String deviceCode) {
        LOGGER.debug("delete({})", deviceCode);
        return monoToCompletable(getTemplate().delete(Query.query(where("id").is(deviceCode)), JdbcDeviceAuthorizationRequest.class))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(Query.query(where("expire_at").lessThan(now)), JdbcDeviceAuthorizationRequest.class))
                .doOnError(error -> LOGGER.error("Unable to purge DeviceAuthorizationRequests", error))
                .observeOn(Schedulers.computation());
    }
}
