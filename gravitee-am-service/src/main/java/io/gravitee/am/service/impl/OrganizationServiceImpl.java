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

import io.gravitee.am.common.context.GraviteeContext;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.am.service.model.UpdateOrganization;
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
public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationServiceImpl.class);

    @Autowired
    private OrganizationRepository organizationRepository;

    @Override
    public Maybe<Organization> findById(String id) {
        LOGGER.debug("Find organization by id: {}", id);
        return organizationRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find organization by id: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error occurs while trying to find organization by id: %s", id), ex));
                });
    }

    @Override
    public Single<List<Organization>> findAll() {
        LOGGER.debug("Find all organizations");
        return organizationRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all organizations", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all organizations", ex));
                });
    }

    @Override
    public Single<Organization> create(NewOrganization newOrganization) {
        LOGGER.debug("Create a new organization {}", newOrganization);

        Organization organization = new Organization();
        organization.setDescription(newOrganization.getDescription());
        organization.setDomainRestrictions(newOrganization.getDomainRestrictions());
        organization.setId(RandomString.generate());
        organization.setName(newOrganization.getName());

        return organizationRepository.create(organization);
    }

    @Override
    public Single<Organization> update(UpdateOrganization updateOrganization) {
        LOGGER.debug("Update an organization {}", updateOrganization.getId());

        return organizationRepository.findById(updateOrganization.getId())
                .switchIfEmpty(Maybe.error(new OrganizationNotFoundException(updateOrganization.getId())))
                .flatMapSingle(oldOrganization -> {
                    Organization organizationToUpdate = new Organization(oldOrganization);
                    organizationToUpdate.setDescription(updateOrganization.getDescription());
                    organizationToUpdate.setDomainRestrictions(updateOrganization.getDomainRestrictions());
                    organizationToUpdate.setName(updateOrganization.getName());

                    return organizationRepository.update(organizationToUpdate);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an organization", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an organization", ex));
                });
    }

    @Override
    public Completable delete(String organizationId) {
        LOGGER.debug("Delete Organization {}", organizationId);
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Maybe.error(new OrganizationNotFoundException(organizationId)))
                .flatMapCompletable(organization -> organizationRepository.delete(organizationId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete organization: {}", organizationId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete organization: %s", organizationId), ex));
                });
    }

    @Override
    public Completable initialize() {
        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(GraviteeContext.getDefaultOrganization());
        defaultOrganization.setName("Default organization");
        defaultOrganization.setDescription("Default organization");
        return Completable.fromSingle(organizationRepository.create(defaultOrganization));
    }
}

