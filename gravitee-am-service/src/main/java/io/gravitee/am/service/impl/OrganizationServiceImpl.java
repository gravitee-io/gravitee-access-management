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
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.am.service.model.PatchOrganization;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.OrganizationAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationServiceImpl.class);

    private final OrganizationRepository organizationRepository;

    private final RoleService roleService;

    private final EntrypointService entrypointService;

    private final AuditService auditService;

    public OrganizationServiceImpl(@Lazy OrganizationRepository organizationRepository,
                                   RoleService roleService,
                                   EntrypointService entrypointService,
                                   AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.roleService = roleService;
        this.entrypointService = entrypointService;
        this.auditService = auditService;
    }

    @Override
    public Single<Organization> findById(String id) {
        LOGGER.debug("Find organization by id: {}", id);
        return organizationRepository.findById(id)
                .switchIfEmpty(Single.error(new OrganizationNotFoundException(id)));
    }

    @Override
    public Flowable<Organization> findAll() {
        return organizationRepository.findAll();
    }

    @Override
    public Maybe<Organization> createDefault() {

        Organization organization = new Organization();
        organization.setId(Organization.DEFAULT);
        organization.setHrids(Collections.singletonList(Organization.DEFAULT.toLowerCase()));
        organization.setName("Default organization");
        organization.setDescription("Default organization");
        organization.setDomainRestrictions(Collections.emptyList());

        // No need to create default organization if one or more organizations already exist.
        return organizationRepository.count()
                .filter(aLong -> aLong == 0)
                .flatMap(aLong -> createInternal(organization, null).toMaybe());
    }

    @Override
    public Single<Organization> createOrUpdate(String organizationId, NewOrganization newOrganization, User byUser) {

        return organizationRepository.findById(organizationId)
                .flatMap(organization -> {
                    Organization toUpdate = new Organization(organization);
                    toUpdate.setName(newOrganization.getName());
                    toUpdate.setDescription(newOrganization.getDescription());
                    toUpdate.setDomainRestrictions(newOrganization.getDomainRestrictions());
                    toUpdate.setHrids(newOrganization.getHrids());

                    return updateInternal(toUpdate, byUser, organization).toMaybe();
                })
                .switchIfEmpty(Single.defer(() -> {
                    Organization toCreate = new Organization();
                    toCreate.setId(organizationId);
                    toCreate.setHrids(newOrganization.getHrids());
                    toCreate.setName(newOrganization.getName());
                    toCreate.setDescription(newOrganization.getDescription());
                    toCreate.setDomainRestrictions(newOrganization.getDomainRestrictions());

                    return createInternal(toCreate, byUser);
                }));
    }

    @Override
    public Single<Organization> update(String organizationId, PatchOrganization patchOrganization, User updatedBy) {

        return findById(organizationId)
                .flatMap(organization -> updateInternal(patchOrganization.patch(organization), updatedBy, organization));
    }

    private Single<Organization> createInternal(Organization toCreate, User owner) {

        Date now = new Date();

        toCreate.setCreatedAt(now);
        toCreate.setUpdatedAt(now);

        // Creates an organization and set ownership.
        return organizationRepository.create(toCreate)
                .flatMap(createdOrganization ->
                        Completable.mergeArrayDelayError(entrypointService.createDefaults(createdOrganization).ignoreElements(),
                                roleService.createDefaultRoles(createdOrganization.getId()))
                                .andThen(Single.just(createdOrganization)))
                .doOnSuccess(organization -> auditService.report(AuditBuilder.builder(OrganizationAuditBuilder.class).type(EventType.ORGANIZATION_CREATED).organization(organization).principal(owner)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(OrganizationAuditBuilder.class).type(EventType.ORGANIZATION_CREATED).organization(toCreate).principal(owner).throwable(throwable)));
    }

    private Single<Organization> updateInternal(Organization organization, User updatedBy, Organization previous) {

        organization.setUpdatedAt(new Date());

        return organizationRepository.update(organization)
                .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(OrganizationAuditBuilder.class).type(EventType.ORGANIZATION_UPDATED).organization(updated).principal(updatedBy).oldValue(previous)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(OrganizationAuditBuilder.class).type(EventType.ORGANIZATION_UPDATED).organization(previous).principal(updatedBy).throwable(throwable)));
    }
}