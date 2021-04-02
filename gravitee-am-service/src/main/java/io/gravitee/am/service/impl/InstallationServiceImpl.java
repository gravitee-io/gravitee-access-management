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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Installation;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.InstallationNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.model.Installation.COCKPIT_INSTALLATION_STATUS;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class InstallationServiceImpl implements InstallationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstallationServiceImpl.class);

    private final InstallationRepository installationRepository;

    public InstallationServiceImpl(@Lazy InstallationRepository installationRepository) {
        this.installationRepository = installationRepository;
    }

    @Override
    public Single<Installation> get() {
        return this.installationRepository.find()
                .switchIfEmpty(Single.error(new InstallationNotFoundException()));
    }

    @Override
    public Single<Installation> getOrInitialize() {
        return this.installationRepository.find()
                .switchIfEmpty(createInternal());
    }

    @Override
    public Single<Installation> setAdditionalInformation(Map<String, String> additionalInformation) {
        return get()
                .flatMap(installation -> {
                    Installation toUpdate = new Installation(installation);
                    toUpdate.setAdditionalInformation(additionalInformation);

                    return updateInternal(toUpdate);
                });
    }

    @Override
    public Single<Installation> addAdditionalInformation(Map<String, String> additionalInformation) {
        return getOrInitialize()
                .doOnSuccess(installation -> installation.getAdditionalInformation().putAll(additionalInformation))
                .flatMap(this::updateInternal);
    }

    @Override
    public Completable delete() {
        return this.installationRepository.find()
                .switchIfEmpty(Single.error(new InstallationNotFoundException()))
                .flatMapCompletable(installation -> installationRepository.delete(installation.getId()))
                .onErrorResumeNext(ex -> {
                    // installation already deleted, continue
                    if (ex instanceof InstallationNotFoundException) {
                        return Completable.complete();
                    }
                    return Completable.error(ex);
                });
    }

    private Single<Installation> createInternal() {

        final Date now = Date.from(Instant.now());
        final Installation installation = new Installation();
        installation.setId(RandomString.generate());
        installation.setCreatedAt(now);
        installation.setUpdatedAt(now);
        installation.setAdditionalInformation(new HashMap<>());

        return installationRepository.create(installation);
    }

    private Single<Installation> updateInternal(Installation toUpdate) {

        final Date now = Date.from(Instant.now());
        toUpdate.setUpdatedAt(now);

        return installationRepository.update(toUpdate);
    }
}
