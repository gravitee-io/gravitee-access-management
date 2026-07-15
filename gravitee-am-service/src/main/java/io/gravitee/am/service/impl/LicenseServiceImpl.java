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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.LicenseService;
import io.gravitee.am.service.exception.InvalidLicenseException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.Objects;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class LicenseServiceImpl implements LicenseService {

    private final LicenseRepository licenseRepository;
    private final EventService eventService;

    public LicenseServiceImpl(@Lazy LicenseRepository licenseRepository,
                              EventService eventService) {
        this.licenseRepository = licenseRepository;
        this.eventService = eventService;
    }

    @Override
    public Flowable<License> findAll() {
        return licenseRepository.findAll();
    }

    @Override
    public Maybe<License> findByReference(ReferenceType referenceType, String referenceId) {
        return licenseRepository.findById(referenceId, referenceType);
    }

    @Override
    public Single<License> createOrUpdate(ReferenceType referenceType, String referenceId, String license) {
        log.debug("Create or update license for {} [{}]", referenceType, referenceId);
        return Single.defer(() -> {
            validate(license);
            return licenseRepository.findById(referenceId, referenceType)
                    .flatMap(existing -> {
                        if (Objects.equals(existing.getLicense(), license)) {
                            return Maybe.just(existing);
                        }
                        existing.setLicense(license);
                        existing.setUpdatedAt(new Date());
                        return licenseRepository.update(existing)
                                .flatMap(updated -> emitEvent(referenceType, referenceId, Action.UPDATE).andThen(Single.just(updated)))
                                .toMaybe();
                    })
                    .switchIfEmpty(Single.defer(() -> {
                        License toCreate = new License();
                        toCreate.setReferenceId(referenceId);
                        toCreate.setReferenceType(referenceType);
                        toCreate.setLicense(license);
                        Date now = new Date();
                        toCreate.setCreatedAt(now);
                        toCreate.setUpdatedAt(now);
                        return licenseRepository.create(toCreate)
                                .flatMap(created -> emitEvent(referenceType, referenceId, Action.CREATE).andThen(Single.just(created)));
                    }));
        });
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId) {
        log.debug("Delete license for {} [{}]", referenceType, referenceId);
        return licenseRepository.findById(referenceId, referenceType)
                .flatMapCompletable(existing -> licenseRepository.delete(referenceId, referenceType)
                        .andThen(emitEvent(referenceType, referenceId, Action.DELETE)));
    }

    @Override
    public void validate(String license) {
        if (license == null || license.isBlank()) {
            throw new InvalidLicenseException("License must be a non-blank base64-encoded value");
        }
        try {
            Base64.getDecoder().decode(license);
        } catch (IllegalArgumentException e) {
            throw new InvalidLicenseException("License is not a valid base64-encoded value");
        }
    }

    private Completable emitEvent(ReferenceType referenceType, String referenceId, Action action) {
        Event event = new Event(Type.LICENSE, new Payload(referenceId, referenceType, referenceId, action));
        return eventService.create(event).ignoreElement();
    }
}
