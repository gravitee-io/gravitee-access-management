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

package io.gravitee.am.gateway.handler.common.service.impl;


import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;

import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class DeviceGatewayServiceImpl implements DeviceGatewayService {
    //Ten hours
    private static final long DEFAULT_DEVICE_EXPIRATION_TIME_SECONDS = 10L * 60L * 60L;

    private DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Flowable<Device> findByDomainAndUser(Domain domain, UserId userId) {
        return dataPlaneRegistry.getDeviceRepository(domain).findByDomainAndClientAndUser(domain.getId(), userId).onErrorResumeNext(ex -> {
            log.error("An error occurs while trying to find Devices by {} {}", domain, userId, ex);
            return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find Devices by %s %s", domain, userId), ex));
        });
    }

    @Override
    public Single<Boolean> deviceExists(Domain domain, String client, UserId user, String rememberDevice, String deviceId) {
        return dataPlaneRegistry.getDeviceRepository(domain).findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(domain.getId(), client, user, rememberDevice, deviceId)
                .isEmpty()
                .doOnSuccess(notFound ->
                        log.debug("call deviceExists(domain:{}, client:{}, userId:{}, rememberDevice:{}, deviceId:{}) result with : {}", domain.getId(), client, user, rememberDevice, deviceId, !notFound));
    }

    @Override
    public Single<Device> create(Domain domain, String client, UserId user, String rememberDevice, String deviceType, Long timeExpirationSeconds, String deviceId) {
        long expiresAt = System.currentTimeMillis() + Optional.ofNullable(timeExpirationSeconds)
                .filter(time -> time > 0L)
                .orElse(DEFAULT_DEVICE_EXPIRATION_TIME_SECONDS) * 1000L;
        return dataPlaneRegistry.getDeviceRepository(domain).create(new Device()
                .setReferenceType(DOMAIN)
                .setReferenceId(domain.getId())
                .setClient(client)
                .setUserId(user)
                .setDeviceIdentifierId(rememberDevice)
                .setType(deviceType)
                .setDeviceId(deviceId)
                .setCreatedAt(new Date())
                .setExpiresAt(new Date(expiresAt))
        ).doOnSuccess(ignoreMe ->
                        log.debug("createDevice(domain:{}, client:{}, userId:{}, rememberDevice:{}, deviceId:{}) successful",
                                domain.getId(), client, user, rememberDevice, deviceId))
                .doOnError(exception ->
                        log.debug("createDevice(domain:{}, client:{}, userId:{}, rememberDevice:{}, deviceId:{}) failed : {}",
                                domain.getId(), client, user, rememberDevice, deviceId, exception.getMessage()));
    }
}
