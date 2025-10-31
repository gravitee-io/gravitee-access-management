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
import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcDevice;
import io.gravitee.am.repository.management.api.DeviceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcDeviceRepository extends AbstractJdbcRepository implements DeviceRepository {

    private static final String REFERENCE_ID_FIELD = "reference_id";
    private static final String REF_TYPE_FIELD = "reference_type";
    private static final String ID_FIELD = "id";
    private static final String CLIENT_FIELD = "client";
    public static final String EXPIRES_AT_FIELD = "expires_at";
    public static final String DEVICE_IDENTIFIER_ID = "device_identifier_id";
    public static final String DEVICE_ID = "device_id";

    protected Device toEntity(JdbcDevice dbEntity) {
        return dbEntity.toEntity();
    }

    @Override
    public Flowable<Device> findByReferenceAndUser(ReferenceType referenceType, String referenceId, UserId userId) {
        LOGGER.debug("findByReferenceAndApplicationAndUser({}, {})", referenceType, referenceId);
        LocalDateTime now = LocalDateTime.now(UTC);
        return fluxToFlowable(getTemplate().select(JdbcDevice.class)
                .matching(Query.query(
                        where(REFERENCE_ID_FIELD).is(referenceId)
                                .and(where(REF_TYPE_FIELD).is(referenceType.name()))
                                .and(userIdMatches(userId))
                                .and(where(EXPIRES_AT_FIELD).greaterThanOrEquals(now))
                ))
                .all())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    protected Criteria userIdMatches(UserId user) {
        Objects.requireNonNull(user.id(), "internal user id");
        return where(USER_ID_FIELD).is(user.id());
    }

    @Override
    public Maybe<Device> findByReferenceAndClientAndUserAndDeviceIdentifierAndDeviceId(
            ReferenceType referenceType, String referenceId, String client, UserId userId, String rememberDevice, String deviceId) {
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(JdbcDevice.class)
                .matching(Query.query(where(REFERENCE_ID_FIELD).is(referenceId)
                        .and(where(REF_TYPE_FIELD).is(referenceType.name()))
                        .and(where(CLIENT_FIELD).is(client))
                        .and(userIdMatches(userId))
                        .and(where(DEVICE_IDENTIFIER_ID).is(rememberDevice))
                        .and(where(DEVICE_ID).is(deviceId))
                        .and(where(EXPIRES_AT_FIELD).greaterThanOrEquals(now))
                ))
                .first())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    protected JdbcDevice toJdbcEntity(Device entity) {
        return JdbcDevice.from(entity);
    }

    @Override
    public Maybe<Device> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(getTemplate().select(JdbcDevice.class)
                .matching(Query.query(where(ID_FIELD).is(id).and(where(EXPIRES_AT_FIELD).greaterThanOrEquals(now))))
                .first())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Device> create(Device item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create remember device with id {}", item.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Device> update(Device item) {
        LOGGER.debug("update remember device with id {}", item.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(JdbcDevice.class)
                .matching(Query.query(where(ID_FIELD).is(id))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(
                getTemplate().delete(JdbcDevice.class).matching(Query.query(where(EXPIRES_AT_FIELD).lessThan(now))).all()
        ).doOnError(error -> LOGGER.error("Unable to purge Devices", error))
                .observeOn(Schedulers.computation());
    }
}
