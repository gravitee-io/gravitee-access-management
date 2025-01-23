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

package io.gravitee.am.management.service.dataplane.impl;


import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.dataplane.DeviceManagementService;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.DeviceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class DeviceManagementServiceImpl implements DeviceManagementService {

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private AuditService auditService;
    
    @Override
    public Flowable<Device> findByDomainAndUser(Domain domain, UserId userId) {
        return dataPlaneRegistry.getDeviceRepository(domain).findByDomainAndClientAndUser(domain.getId(), userId).onErrorResumeNext(ex -> {
            log.error("An error occurs while trying to find Devices by {} {}", domain, userId, ex);
            return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find Devices by %s %s", domain, userId), ex));
        });
    }

    @Override
    public Completable delete(Domain domain, UserId user, String deviceId, User principal) {
        return dataPlaneRegistry.getDeviceRepository(domain).findById(deviceId)
                .switchIfEmpty(Maybe.error(new DeviceNotFoundException(deviceId)))
                .flatMap(device -> {
                    if (DOMAIN.equals(device.getReferenceType()) && device.getReferenceId().equals(domain) && device.getUserId().id().equals(user.id())) {
                        return dataPlaneRegistry.getDeviceRepository(domain).delete(deviceId).andThen(Maybe.just(device));
                    } else {
                        return Maybe.error(new DeviceNotFoundException(deviceId));
                    }
                }).onErrorResumeNext(ex -> {
                    if (ex instanceof DeviceNotFoundException) {
                        return Maybe.error(ex);
                    }
                    log.error("An error occurs while trying to delete factor: {}", deviceId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete factor: %s", deviceId), ex));
                })
                .doOnSuccess(device -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.DEVICE_DELETED).reference(domain.asReference()).deletedDevice(device)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.DEVICE_DELETED).reference(domain.asReference()).throwable(throwable)))
                .ignoreElement();
    }
}
