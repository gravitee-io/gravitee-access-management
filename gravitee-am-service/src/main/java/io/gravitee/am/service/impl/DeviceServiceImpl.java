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

package io.gravitee.am.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Device;
import io.gravitee.am.repository.management.api.DeviceRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.exception.DeviceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DeviceServiceImpl implements DeviceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceServiceImpl.class);

    private static final long DEFAULT_DEVICE_EXPIRATION_TIME_SECONDS = 7200L;

    @Lazy
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AuditService auditService;

    @Override
    public Flowable<Device> findByDomainAndUser(String domain, String user) {
        return deviceRepository.findByDomainAndClientAndUser(domain, user).onErrorResumeNext(ex -> {
            LOGGER.error("An error occurs while trying to find Devices by {} {}", domain, user, ex);
            return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find Devices by %s %s", domain, user), ex));
        });
    }

    @Override
    public Single<Boolean> deviceExists(String domain, String client, String user, String rememberDevice, String deviceId) {
        return deviceRepository.findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(domain, client, user, rememberDevice, deviceId).isEmpty();
    }

    @Override
    public Single<Device> create(String domain, String client, String user, String rememberDevice, String deviceType, Long timeExpirationSeconds, String deviceId) {
        long expiresAt = System.currentTimeMillis() + Optional.ofNullable(timeExpirationSeconds)
                .filter(Objects::nonNull)
                .filter(time -> time > 0L)
                .orElse(DEFAULT_DEVICE_EXPIRATION_TIME_SECONDS) * 1000L;
        return deviceRepository.create(new Device()
                .setReferenceType(DOMAIN)
                .setReferenceId(domain)
                .setClient(client)
                .setUserId(user)
                .setDeviceIdentifierId(rememberDevice)
                .setType(deviceType)
                .setDeviceId(deviceId)
                .setCreatedAt(new Date())
                .setExpiresAt(new Date(expiresAt))
        );
    }

    @Override
    public Completable delete(String domain, String user, String deviceId, User principal) {
        return deviceRepository.findById(deviceId)
                .switchIfEmpty(Maybe.error(new DeviceNotFoundException(deviceId)))
                .flatMapCompletable(device -> {
                    if (DOMAIN.equals(device.getReferenceType()) && device.getReferenceId().equals(domain) && device.getUserId().equals(user)) {
                        return deviceRepository.delete(deviceId).andThen(Completable.complete());
                    } else {
                        return Completable.error(new DeviceNotFoundException(deviceId));
                    }
                }).onErrorResumeNext(ex -> {
                    if (ex instanceof DeviceNotFoundException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete factor: {}", deviceId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete factor: %s", deviceId), ex));
                })
                .doOnComplete(() -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.DEVICE_DELETED)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.DEVICE_DELETED).throwable(throwable)));
    }
}
