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

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.service.LicenseService;
import io.gravitee.am.service.exception.InvalidLicenseException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class LicenseServiceImpl implements LicenseService {

    private final LicenseRepository licenseRepository;

    public LicenseServiceImpl(@Lazy LicenseRepository licenseRepository) {
        this.licenseRepository = licenseRepository;
    }

    @Override
    public Single<License> createOrUpdate(ReferenceType referenceType, String referenceId, String license) {
        log.debug("Create or update license for {} [{}]", referenceType, referenceId);
        return Single.defer(() -> {
            validate(license);
            return licenseRepository.findById(referenceId, referenceType)
                    .flatMap(existing -> {
                        existing.setLicense(license);
                        existing.setUpdatedAt(new Date());
                        return licenseRepository.update(existing).toMaybe();
                    })
                    .switchIfEmpty(Single.defer(() -> {
                        License toCreate = new License();
                        toCreate.setReferenceId(referenceId);
                        toCreate.setReferenceType(referenceType);
                        toCreate.setLicense(license);
                        Date now = new Date();
                        toCreate.setCreatedAt(now);
                        toCreate.setUpdatedAt(now);
                        return licenseRepository.create(toCreate);
                    }));
        });
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId) {
        log.debug("Delete license for {} [{}]", referenceType, referenceId);
        return licenseRepository.delete(referenceId, referenceType);
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
}
