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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcDevice;
import io.gravitee.am.repository.management.api.DeviceRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

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
    private static final String USER_FIELD = "user_id";
    public static final String EXPIRES_AT_FIELD = "expires_at";
    public static final String DEVICE_IDENTIFIER_ID = "device_identifier_id";
    public static final String DEVICE_ID = "device_id";

    protected Device toEntity(JdbcDevice entity) {
        return mapper.map(entity, Device.class);
    }

    @Override
    public Flowable<Device> findByReferenceAndUser(ReferenceType referenceType, String referenceId, String user) {
        LOGGER.debug("findByReferenceAndApplicationAndUser({}, {})", referenceType, referenceId);
        return fluxToFlowable(dbClient.select()
                .from(JdbcDevice.class)
                .matching(from(
                        where(REFERENCE_ID_FIELD).is(referenceId)
                                .and(where(REF_TYPE_FIELD).is(referenceType.name()))
                                .and(where(USER_FIELD).is(user))
                )).fetch()
                .all())
                .map(this::toEntity);
    }

    @Override
    public Maybe<Device> findByReferenceAndClientAndUserAndDeviceIdentifierAndDeviceId(
            ReferenceType referenceType, String referenceId, String client, String user, String rememberDevice, String deviceId) {
        return monoToMaybe(dbClient.select()
                .from(JdbcDevice.class)
                .matching(where(REFERENCE_ID_FIELD).is(referenceId)
                        .and(where(REF_TYPE_FIELD).is(referenceType.name()))
                        .and(where(CLIENT_FIELD).is(client))
                        .and(where(USER_FIELD).is(user))
                        .and(where(DEVICE_IDENTIFIER_ID).is(rememberDevice))
                        .and(where(DEVICE_ID).is(deviceId)))
                .fetch()
                .first())
                .map(this::toEntity);
    }

    protected JdbcDevice toJdbcEntity(Device entity) {
        return mapper.map(entity, JdbcDevice.class);
    }

    @Override
    public Maybe<Device> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(dbClient.select()
                .from(JdbcDevice.class)
                .matching(from(where(ID_FIELD).is(id)))
                .fetch()
                .first())
                .map(this::toEntity);
    }

    @Override
    public Single<Device> create(Device item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create remember device with id {}", item.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcDevice.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Device> update(Device item) {
        LOGGER.debug("update remember device with id {}", item.getId());
        Mono<Integer> action = dbClient.update()
                .table(JdbcDevice.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(dbClient.delete()
                .from(JdbcDevice.class)
                .matching(from(where(ID_FIELD).is(id)))
                .fetch().rowsUpdated());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(
                dbClient.delete().from(JdbcDevice.class).matching(where(EXPIRES_AT_FIELD).lessThan(now)).then()
        ).doOnError(error -> LOGGER.error("Unable to purge Devices", error));
    }
}
