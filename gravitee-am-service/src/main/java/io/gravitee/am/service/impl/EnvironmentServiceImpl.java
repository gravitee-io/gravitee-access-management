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
import io.gravitee.am.model.Environment;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.am.service.model.UpdateEnvironment;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Florent CHAMFROY (forent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentServiceImpl.class);

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Override
    public Maybe<Environment> findById(String id) {
        LOGGER.debug("Find environment by id: {}", id);
        return environmentRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find environment by id: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error occurs while trying to find environment by id: %s", id), ex));
                });
    }

    @Override
    public Single<List<Environment>> findAll() {
        LOGGER.debug("Find all environments");
        return environmentRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all environments", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all environments", ex));
                });
    }

    @Override
    public Single<List<Environment>> findByOrganization(String organizationId) {
        LOGGER.debug("Find all environments by organization");
        return environmentRepository.findByOrganization(organizationId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all environments by organization : {}", organizationId, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all environments by organization : " + organizationId, ex));
                });
    }

    @Override
    public Single<Environment> create(NewEnvironment newEnvironment) {
        LOGGER.debug("Create a new environment {}", newEnvironment);

        Environment environment = new Environment();
        environment.setDescription(newEnvironment.getDescription());
        environment.setDomainRestrictions(newEnvironment.getDomainRestrictions());
        environment.setId(RandomString.generate());
        environment.setName(newEnvironment.getName());

        return environmentRepository.create(environment);
    }

    @Override
    public Single<Environment> update(UpdateEnvironment updateEnvironment) {
        LOGGER.debug("Update an environment {}", updateEnvironment.getId());

        return environmentRepository.findById(updateEnvironment.getId())
                .switchIfEmpty(Maybe.error(new EnvironmentNotFoundException(updateEnvironment.getId())))
                .flatMapSingle(oldEnvironment -> {
                    Environment environmentToUpdate = new Environment(oldEnvironment);
                    environmentToUpdate.setDescription(updateEnvironment.getDescription());
                    environmentToUpdate.setDomainRestrictions(updateEnvironment.getDomainRestrictions());
                    environmentToUpdate.setName(updateEnvironment.getName());

                    return environmentRepository.update(environmentToUpdate);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an environment", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an environment", ex));
                });
    }

    @Override
    public Completable delete(String environmentId) {
        LOGGER.debug("Delete Environment {}", environmentId);
        return environmentRepository.findById(environmentId)
                .switchIfEmpty(Maybe.error(new EnvironmentNotFoundException(environmentId)))
                .flatMapCompletable(environment -> environmentRepository.delete(environmentId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete environment: {}", environmentId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete environment: %s", environmentId), ex));
                });
    }

    @Override
    public Completable initialize() {
        // TODO Auto-generated method stub
        return null;
    }
}

