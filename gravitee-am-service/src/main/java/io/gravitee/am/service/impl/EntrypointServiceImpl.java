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
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.exception.InvalidEntrypointException;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.am.service.model.UpdateEntrypoint;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.EntrypointAuditBuilder;
import io.gravitee.am.service.validators.VirtualHostValidator;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EntrypointServiceImpl implements EntrypointService {

    private final Logger LOGGER = LoggerFactory.getLogger(EntrypointServiceImpl.class);

    private final EntrypointRepository entrypointRepository;

    private final OrganizationService organizationService;

    private final AuditService auditService;

    public EntrypointServiceImpl(@Lazy EntrypointRepository entrypointRepository,
                                 @Lazy OrganizationService organizationService,
                                 AuditService auditService) {
        this.entrypointRepository = entrypointRepository;
        this.organizationService = organizationService;
        this.auditService = auditService;
    }

    @Override
    public Single<Entrypoint> findById(String id, String organizationId) {

        LOGGER.debug("Find entrypoint by id {} and organizationId {}", id, organizationId);

        return entrypointRepository.findById(id, organizationId)
                .switchIfEmpty(Single.error(new EntrypointNotFoundException(id)));
    }

    @Override
    public Flowable<Entrypoint> findAll(String organizationId) {

        LOGGER.debug("Find all entrypoints by organizationId {}", organizationId);

        return entrypointRepository.findAll(organizationId);
    }

    @Override
    public Single<Entrypoint> create(String organizationId, NewEntrypoint newEntrypoint, User principal) {

        LOGGER.debug("Create a new entrypoint {} for organization {}", newEntrypoint, organizationId);

        Entrypoint toCreate = new Entrypoint();
        toCreate.setOrganizationId(organizationId);
        toCreate.setName(newEntrypoint.getName());
        toCreate.setDescription(newEntrypoint.getDescription());
        toCreate.setUrl(newEntrypoint.getUrl());
        toCreate.setTags(newEntrypoint.getTags());

        return createInternal(toCreate, principal);
    }

    @Override
    public Flowable<Entrypoint> createDefaults(Organization organization) {

        List<Single<Entrypoint>> toCreateObsList = new ArrayList<>();

        if (CollectionUtils.isEmpty(organization.getDomainRestrictions())) {
            Entrypoint toCreate = new Entrypoint();
            toCreate.setName("Default");
            toCreate.setDescription("Default entrypoint");
            toCreate.setUrl("https://auth.company.com");
            toCreate.setTags(Collections.emptyList());
            toCreate.setOrganizationId(organization.getId());
            toCreate.setDefaultEntrypoint(true);

            toCreateObsList.add(createInternal(toCreate, null));
        } else {
            for (int i = 0; i < organization.getDomainRestrictions().size(); i++) {
                Entrypoint toCreate = new Entrypoint();
                String domainRestriction = organization.getDomainRestrictions().get(i);
                toCreate.setName(domainRestriction);
                toCreate.setDescription("Entrypoint " + domainRestriction);
                toCreate.setUrl("https://" + domainRestriction);
                toCreate.setTags(Collections.emptyList());
                toCreate.setOrganizationId(organization.getId());
                toCreate.setDefaultEntrypoint(i == 0);
                toCreateObsList.add(createInternal(toCreate, null));
            }
        }

        return Single.mergeDelayError(toCreateObsList);
    }

    @Override
    public Single<Entrypoint> update(String entrypointId, String organizationId, UpdateEntrypoint updateEntrypoint, User principal) {

        LOGGER.debug("Update an existing entrypoint {}", updateEntrypoint);

        return findById(entrypointId, organizationId)
                .flatMap(oldEntrypoint -> {
                    Entrypoint toUpdate = new Entrypoint(oldEntrypoint);
                    toUpdate.setName(updateEntrypoint.getName());
                    toUpdate.setDescription(updateEntrypoint.getDescription());
                    toUpdate.setUrl(updateEntrypoint.getUrl());
                    toUpdate.setTags(updateEntrypoint.getTags());
                    toUpdate.setUpdatedAt(new Date());

                    return validate(toUpdate, oldEntrypoint)
                            .andThen(entrypointRepository.update(toUpdate)
                                    .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).principal(principal).type(EventType.ENTRYPOINT_UPDATED).entrypoint(updated).oldValue(oldEntrypoint)))
                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).principal(principal).type(EventType.ENTRYPOINT_UPDATED).throwable(throwable))));
                });
    }

    @Override
    public Completable delete(String id, String organizationId, User principal) {

        LOGGER.debug("Delete entrypoint by id {} and organizationId {}", id, organizationId);

        return findById(id, organizationId)
                .flatMapCompletable(entrypoint -> entrypointRepository.delete(id)
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).principal(principal).type(EventType.ENTRYPOINT_DELETED).entrypoint(entrypoint)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).principal(principal).type(EventType.ENTRYPOINT_DELETED).throwable(throwable))));
    }

    private Single<Entrypoint> createInternal(Entrypoint toCreate, User principal) {

        Date now = new Date();

        toCreate.setId(UUID.random().toString());
        toCreate.setCreatedAt(now);
        toCreate.setUpdatedAt(now);

        return validate(toCreate)
                .andThen(entrypointRepository.create(toCreate)
                        .doOnSuccess(entrypoint -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).entrypoint(entrypoint).principal(principal).type(EventType.ENTRYPOINT_CREATED)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntrypointAuditBuilder.class).referenceId(toCreate.getOrganizationId()).principal(principal).type(EventType.ENTRYPOINT_CREATED).throwable(throwable))));
    }

    private Completable validate(Entrypoint entrypoint) {
        return validate(entrypoint, null);
    }

    private Completable validate(Entrypoint entrypoint, Entrypoint oldEntrypoint) {

        if (oldEntrypoint != null && oldEntrypoint.isDefaultEntrypoint()) {
            // Only the url of the default entrypoint can be updated.
            if (!entrypoint.getName().equals(oldEntrypoint.getName())
                    || !entrypoint.getDescription().equals(oldEntrypoint.getDescription())
                    || !entrypoint.getTags().equals(oldEntrypoint.getTags())) {
                return Completable.error(new InvalidEntrypointException("Only the url of the default entrypoint can be updated."));
            }
        }

        try {
            // Try to instantiate uri to check if it's a valid endpoint url.
            URL url = new URL(entrypoint.getUrl());
            if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                throw new MalformedURLException();
            }

            return organizationService.findById(entrypoint.getOrganizationId())
                    .flatMapCompletable(organization -> {
                        String hostWithoutPort = url.getHost().split(":")[0];
                        if (!VirtualHostValidator.isValidDomainOrSubDomain(hostWithoutPort, organization.getDomainRestrictions())) {
                            return Completable.error(new InvalidEntrypointException("Host [" + hostWithoutPort + "] must be a subdomain of " + organization.getDomainRestrictions()));
                        }

                        return Completable.complete();
                    });
        } catch (MalformedURLException e) {
            return Completable.error(new InvalidEntrypointException("Entrypoint must have a valid url."));
        }
    }
}