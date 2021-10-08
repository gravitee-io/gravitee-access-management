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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.DeviceIdentifierRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.DeviceIdentifierNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewDeviceIdentifier;
import io.gravitee.am.service.model.UpdateDeviceIdentifier;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.DeviceIdentifierAuditBuilder;
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

/**
 * @author Rémi SULTAN (remi.sultqn at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DeviceIdentifierServiceImpl implements DeviceIdentifierService {

    private final Logger LOGGER = LoggerFactory.getLogger(DeviceIdentifierServiceImpl.class);

    @Lazy
    @Autowired
    private DeviceIdentifierRepository deviceIdentifierRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<DeviceIdentifier> findById(String id) {
        LOGGER.debug("Find device identifier by ID: {}", id);
        return deviceIdentifierRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a device identifier using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a device identifier using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<DeviceIdentifier> findByDomain(String domain) {
        LOGGER.debug("Find device identifiers by domain: {}", domain);
        return deviceIdentifierRepository.findByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find device identifiers by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find device identifiers by domain", ex));
                });
    }

    @Override
    public Single<DeviceIdentifier> create(String domain, NewDeviceIdentifier newDeviceIdentifier, User principal) {
        LOGGER.debug("Create a new device identifier {} for domain {}", newDeviceIdentifier, domain);

        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        deviceIdentifier.setId(newDeviceIdentifier.getId() == null ? RandomString.generate() : newDeviceIdentifier.getId());
        deviceIdentifier.setReferenceId(domain);
        deviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        deviceIdentifier.setName(newDeviceIdentifier.getName());
        deviceIdentifier.setType(newDeviceIdentifier.getType());
        deviceIdentifier.setConfiguration(newDeviceIdentifier.getConfiguration());
        deviceIdentifier.setCreatedAt(new Date());
        deviceIdentifier.setUpdatedAt(deviceIdentifier.getCreatedAt());

        return deviceIdentifierRepository.create(deviceIdentifier)
                .flatMap(rd -> {
                    // create event for sync process
                    Event event = new Event(Type.DEVICE_IDENTIFIER, new Payload(rd.getId(), rd.getReferenceType(), rd.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(rd));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a device identifier", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a device identifier", ex));
                })
                .doOnSuccess(detection -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_CREATED).deviceIdentifier(detection)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_CREATED).throwable(throwable)));
    }

    @Override
    public Single<DeviceIdentifier> update(String domain, String id, UpdateDeviceIdentifier updateDeviceIdentifier, User principal) {
        LOGGER.debug("Update device identifier {} for domain {}", id, domain);

        return deviceIdentifierRepository.findById(id)
                .switchIfEmpty(Maybe.error(new DeviceIdentifierNotFoundException(id)))
                //
                .flatMapSingle(oldDeviceIdentifier -> {
                    DeviceIdentifier deviceIdentifierToUpdate = new DeviceIdentifier(oldDeviceIdentifier);
                    deviceIdentifierToUpdate.setName(updateDeviceIdentifier.getName());
                    deviceIdentifierToUpdate.setConfiguration(updateDeviceIdentifier.getConfiguration());
                    deviceIdentifierToUpdate.setUpdatedAt(new Date());

                    return  deviceIdentifierRepository.update(deviceIdentifierToUpdate)
                            .flatMap(detection -> {
                                // create event for sync process
                                Event event = new Event(Type.DEVICE_IDENTIFIER, new Payload(detection.getId(), detection.getReferenceType(), detection.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(detection));
                            })
                            .doOnSuccess(detection -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_UPDATED).oldValue(oldDeviceIdentifier).deviceIdentifier(detection)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update device identifier", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update device identifier", ex));
                });
    }

    @Override
    public Completable delete(String domainId, String deviceIdentifier, User principal) {
        LOGGER.debug("Delete device identifier {}", deviceIdentifier);

        return deviceIdentifierRepository.findById(deviceIdentifier)
                .switchIfEmpty(Maybe.error(new DeviceIdentifierNotFoundException(deviceIdentifier)))
                .flatMapCompletable(toDelete -> {
                    // create event for sync process
                    Event event = new Event(Type.DEVICE_IDENTIFIER, new Payload(toDelete.getId(), ReferenceType.DOMAIN, domainId, Action.DELETE));
                    return deviceIdentifierRepository.delete(toDelete.getId())
                            .andThen(eventService.create(event))
                            .ignoreElement()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_DELETED).deviceIdentifier(toDelete)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(DeviceIdentifierAuditBuilder.class).principal(principal).type(EventType.DEVICE_IDENTIFIER_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete device identifier: {}", deviceIdentifier, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete device identifier: %s", deviceIdentifier), ex));
                });
    }
}
