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
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.EnvironmentAuditBuilder;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.Collections;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentServiceImpl.class);

    private final EnvironmentRepository environmentRepository;

    private final OrganizationService organizationService;

    private final AuditService auditService;

    public EnvironmentServiceImpl(
        @Lazy EnvironmentRepository environmentRepository,
        OrganizationService organizationService,
        AuditService auditService
    ) {
        this.environmentRepository = environmentRepository;
        this.organizationService = organizationService;
        this.auditService = auditService;
    }

    @Override
    public Single<Environment> findById(String id, String organizationId) {
        LOGGER.debug("Find environment by id: {}", id);
        return environmentRepository.findById(id, organizationId).switchIfEmpty(Single.error(new EnvironmentNotFoundException(id)));
    }

    @Override
    public Maybe<Environment> createDefault() {
        Environment environment = new Environment();
        environment.setId(Environment.DEFAULT);
        environment.setName("Default environment");
        environment.setDescription("Default environment");
        environment.setOrganizationId(Organization.DEFAULT);
        environment.setDomainRestrictions(Collections.emptyList());

        // No need to create default organization of one or more organizations already exist.
        return environmentRepository.count().filter(aLong -> aLong == 0).flatMap(aLong -> createInternal(environment, null).toMaybe());
    }

    @Override
    public Single<Environment> createOrUpdate(String organizationId, String environmentId, NewEnvironment newEnvironment, User byUser) {
        return environmentRepository
            .findById(environmentId, organizationId)
            .flatMap(
                environment -> {
                    environment.setName(newEnvironment.getName());
                    environment.setDescription(newEnvironment.getDescription());
                    environment.setDomainRestrictions(newEnvironment.getDomainRestrictions());

                    return updateInternal(environment, byUser).toMaybe();
                }
            )
            .switchIfEmpty(
                Single.defer(
                    () ->
                        organizationService
                            .findById(organizationId)
                            .map(
                                organization -> {
                                    Environment toCreate = new Environment();
                                    toCreate.setId(environmentId);
                                    toCreate.setName(newEnvironment.getName());
                                    toCreate.setDescription(newEnvironment.getDescription());
                                    toCreate.setOrganizationId(organization.getId());
                                    toCreate.setDomainRestrictions(newEnvironment.getDomainRestrictions());

                                    return toCreate;
                                }
                            )
                            .flatMap(toCreate -> createInternal(toCreate, byUser))
                )
            );
    }

    private Single<Environment> createInternal(Environment toCreate, User createdBy) {
        Date now = new Date();

        toCreate.setCreatedAt(now);
        toCreate.setUpdatedAt(now);

        return environmentRepository
            .create(toCreate)
            .doOnSuccess(
                environment ->
                    auditService.report(
                        AuditBuilder
                            .builder(EnvironmentAuditBuilder.class)
                            .type(EventType.ENVIRONMENT_CREATED)
                            .environment(environment)
                            .principal(createdBy)
                    )
            )
            .doOnError(
                throwable ->
                    auditService.report(
                        AuditBuilder
                            .builder(EnvironmentAuditBuilder.class)
                            .type(EventType.ENVIRONMENT_CREATED)
                            .environment(toCreate)
                            .principal(createdBy)
                            .throwable(throwable)
                    )
            );
    }

    private Single<Environment> updateInternal(Environment toUpdate, User updatedBy) {
        toUpdate.setUpdatedAt(new Date());

        return environmentRepository
            .update(toUpdate)
            .doOnSuccess(
                updated ->
                    auditService.report(
                        AuditBuilder
                            .builder(EnvironmentAuditBuilder.class)
                            .type(EventType.ENVIRONMENT_UPDATED)
                            .environment(updated)
                            .principal(updatedBy)
                            .oldValue(toUpdate)
                    )
            )
            .doOnError(
                throwable ->
                    auditService.report(
                        AuditBuilder
                            .builder(EnvironmentAuditBuilder.class)
                            .type(EventType.ENVIRONMENT_UPDATED)
                            .environment(toUpdate)
                            .principal(updatedBy)
                            .throwable(throwable)
                    )
            );
    }
}
