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
import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.LicenseService;
import io.gravitee.am.service.exception.InvalidLicenseException;
import io.gravitee.am.service.model.GraviteeLicense;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.LicenseAuditBuilder;
import io.gravitee.node.api.license.LicenseFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.Objects;

/**
 * @author GraviteeSource Team
 */
@CustomLog
@Component
public class LicenseServiceImpl implements LicenseService {

    private final LicenseRepository licenseRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final LicenseFactory licenseFactory;

    public LicenseServiceImpl(@Lazy LicenseRepository licenseRepository,
                              EventService eventService,
                              AuditService auditService,
                              @Lazy LicenseFactory licenseFactory) {
        this.licenseRepository = licenseRepository;
        this.eventService = eventService;
        this.auditService = auditService;
        this.licenseFactory = licenseFactory;
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
        return Maybe.<License>defer(() -> {
                    validate(license);
                    return licenseRepository.findById(referenceId, referenceType);
                })
                .doOnError(error -> reportFailure(EventType.ORGANIZATION_LICENSE_UPDATED, referenceType, referenceId, error))
                .flatMap(existing -> {
                    if (Objects.equals(existing.getLicense(), license)) {
                        log.debug("License unchanged for referenceType={}, referenceId={}, skipping", referenceType, referenceId);
                        return Maybe.just(existing);
                    }
                    final GraviteeLicense previous = describe(referenceType, referenceId, existing.getLicense());
                    existing.setLicense(license);
                    existing.setUpdatedAt(new Date());
                    return licenseRepository.update(existing)
                            .flatMap(updated -> emitEvent(referenceType, referenceId, Action.UPDATE).andThen(Single.just(updated)))
                            .doOnSuccess(updated -> {
                                final GraviteeLicense current = describe(referenceType, referenceId, updated.getLicense());
                                log.info("License updated for referenceType={}, referenceId={}, tier={} -> {}, expiresAt={}",
                                        referenceType, referenceId, tierOf(previous), tierOf(current), expiresAtOf(current));
                                reportChange(EventType.ORGANIZATION_LICENSE_UPDATED, referenceType, referenceId, previous, current);
                            })
                            .doOnError(error -> reportFailure(EventType.ORGANIZATION_LICENSE_UPDATED, referenceType, referenceId, error))
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
                            .flatMap(created -> emitEvent(referenceType, referenceId, Action.CREATE).andThen(Single.just(created)))
                            .doOnSuccess(created -> {
                                final GraviteeLicense current = describe(referenceType, referenceId, created.getLicense());
                                log.info("License created for referenceType={}, referenceId={}, tier={}, expiresAt={}",
                                        referenceType, referenceId, tierOf(current), expiresAtOf(current));
                                reportChange(EventType.ORGANIZATION_LICENSE_CREATED, referenceType, referenceId, null, current);
                            })
                            .doOnError(error -> reportFailure(EventType.ORGANIZATION_LICENSE_CREATED, referenceType, referenceId, error));
                }));
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId) {
        return licenseRepository.findById(referenceId, referenceType)
                .doOnComplete(() -> log.debug("No license to delete for referenceType={}, referenceId={}", referenceType, referenceId))
                .flatMapCompletable(existing -> {
                    final GraviteeLicense previous = describe(referenceType, referenceId, existing.getLicense());
                    return licenseRepository.delete(referenceId, referenceType)
                            .andThen(emitEvent(referenceType, referenceId, Action.DELETE))
                            .doOnComplete(() -> {
                                log.info("License deleted for referenceType={}, referenceId={}, previousTier={}",
                                        referenceType, referenceId, tierOf(previous));
                                reportChange(EventType.ORGANIZATION_LICENSE_DELETED, referenceType, referenceId, previous, null);
                            });
                })
                .doOnError(error -> reportFailure(EventType.ORGANIZATION_LICENSE_DELETED, referenceType, referenceId, error));
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

    /**
     * Decodes the entitlements granted by a raw license so they can be logged and audited without
     * ever exposing the signed license itself.
     *
     * @return the decoded metadata, or {@code null} when the license cannot be read.
     */
    private GraviteeLicense describe(ReferenceType referenceType, String referenceId, String rawLicense) {
        try {
            var license = licenseFactory.create(referenceType.name(), referenceId, rawLicense);
            return GraviteeLicense.builder()
                    .tier(license.getTier())
                    .packs(license.getPacks())
                    .features(license.getFeatures())
                    .expiresAt(license.getExpirationDate())
                    .isExpired(license.isExpired())
                    .scope(license.getReferenceType())
                    .build();
        } catch (Exception e) {
            log.warn("Cannot read license details for referenceType={}, referenceId={}", referenceType, referenceId, e);
            return null;
        }
    }

    private void reportChange(String type, ReferenceType referenceType, String referenceId, GraviteeLicense previous, GraviteeLicense current) {
        if (isNotAuditable(referenceType)) {
            return;
        }
        auditService.report(AuditBuilder.builder(LicenseAuditBuilder.class)
                .type(type)
                .organization(referenceId)
                .oldValue(previous)
                .license(current));
    }

    private void reportFailure(String type, ReferenceType referenceType, String referenceId, Throwable throwable) {
        if (isNotAuditable(referenceType)) {
            return;
        }
        auditService.report(AuditBuilder.builder(LicenseAuditBuilder.class)
                .type(type)
                .organization(referenceId)
                .throwable(throwable));
    }

    private static boolean isNotAuditable(ReferenceType referenceType) {
        return referenceType != ReferenceType.ORGANIZATION;
    }

    private static String tierOf(GraviteeLicense license) {
        return license == null ? null : license.getTier();
    }

    private static Date expiresAtOf(GraviteeLicense license) {
        return license == null ? null : license.getExpiresAt();
    }
}
