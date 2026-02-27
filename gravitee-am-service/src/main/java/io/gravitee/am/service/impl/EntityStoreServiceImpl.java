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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.repository.management.api.EntityStoreRepository;
import io.gravitee.am.repository.management.api.EntityStoreVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EntityStoreService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.EntityStoreNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewEntityStore;
import io.gravitee.am.service.model.UpdateEntityStore;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.EntityStoreAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Component
@Primary
public class EntityStoreServiceImpl implements EntityStoreService {

    private final Logger LOGGER = LoggerFactory.getLogger(EntityStoreServiceImpl.class);

    private final EntityStoreRepository entityStoreRepository;
    private final EntityStoreVersionRepository entityStoreVersionRepository;
    private final AuditService auditService;

    public EntityStoreServiceImpl(@Lazy EntityStoreRepository entityStoreRepository,
                                  @Lazy EntityStoreVersionRepository entityStoreVersionRepository,
                                  AuditService auditService) {
        this.entityStoreRepository = entityStoreRepository;
        this.entityStoreVersionRepository = entityStoreVersionRepository;
        this.auditService = auditService;
    }

    @Override
    public Flowable<EntityStore> findByDomain(String domainId) {
        LOGGER.debug("Find entity stores by domain: {}", domainId);
        return entityStoreRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find entity stores by domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find entity stores by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<EntityStore> findById(String id) {
        LOGGER.debug("Find entity store by ID: {}", id);
        return entityStoreRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an entity store using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an entity store using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<EntityStore> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("Find entity store by domain {} and ID: {}", domainId, id);
        return entityStoreRepository.findByDomainAndId(domainId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an entity store by domain {} and ID: {}", domainId, id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an entity store by domain %s and ID: %s", domainId, id), ex));
                });
    }

    @Override
    public Single<EntityStore> create(Domain domain, NewEntityStore request, User principal) {
        LOGGER.debug("Create a new entity store {} for domain {}", request, domain.getId());

        EntityStore entityStore = new EntityStore();
        entityStore.setId(RandomString.generate());
        entityStore.setDomainId(domain.getId());
        entityStore.setName(request.getName());
        entityStore.setLatestVersion(1);
        entityStore.setCreatedAt(new Date());
        entityStore.setUpdatedAt(entityStore.getCreatedAt());

        return entityStoreRepository.create(entityStore)
                .flatMap(created -> {
                    EntityStoreVersion version = new EntityStoreVersion();
                    version.setId(RandomString.generate());
                    version.setEntityStoreId(created.getId());
                    version.setVersion(1);
                    version.setContent(request.getContent());
                    version.setCommitMessage(request.getCommitMessage());
                    version.setCreatedAt(created.getCreatedAt());
                    version.setCreatedBy(principal != null ? principal.getId() : null);

                    return entityStoreVersionRepository.create(version)
                            .map(v -> created);
                })
                .doOnSuccess(es -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_CREATED).entityStore(es)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_CREATED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an entity store", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an entity store", ex));
                });
    }

    @Override
    public Single<EntityStore> update(Domain domain, String id, UpdateEntityStore request, User principal) {
        LOGGER.debug("Update entity store {} for domain {}", id, domain.getId());

        return entityStoreRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Single.error(new EntityStoreNotFoundException(id)))
                .flatMap(existing -> {
                    if (request.getName() != null) {
                        existing.setName(request.getName());
                    }

                    int newVersion = existing.getLatestVersion() + 1;
                    existing.setLatestVersion(newVersion);
                    existing.setUpdatedAt(new Date());

                    return entityStoreRepository.update(existing)
                            .flatMap(updated -> {
                                String content = request.getContent();
                                if (content == null) {
                                    return entityStoreVersionRepository.findLatestByEntityStoreId(id)
                                            .map(EntityStoreVersion::getContent)
                                            .defaultIfEmpty("")
                                            .flatMap(prevContent -> createVersionRecord(updated, newVersion, prevContent, request.getCommitMessage(), principal));
                                }
                                return createVersionRecord(updated, newVersion, content, request.getCommitMessage(), principal);
                            })
                            .doOnSuccess(es -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_UPDATED).entityStore(es)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an entity store", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an entity store", ex));
                });
    }

    private Single<EntityStore> createVersionRecord(EntityStore entityStore, int versionNum, String content, String commitMessage, User principal) {
        EntityStoreVersion version = new EntityStoreVersion();
        version.setId(RandomString.generate());
        version.setEntityStoreId(entityStore.getId());
        version.setVersion(versionNum);
        version.setContent(content);
        version.setCommitMessage(commitMessage);
        version.setCreatedAt(new Date());
        version.setCreatedBy(principal != null ? principal.getId() : null);

        return entityStoreVersionRepository.create(version)
                .map(v -> entityStore);
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        LOGGER.debug("Delete entity store {}", id);

        return entityStoreRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new EntityStoreNotFoundException(id)))
                .flatMapCompletable(es -> entityStoreVersionRepository.deleteByEntityStoreId(id)
                        .andThen(entityStoreRepository.delete(id))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_DELETED).entityStore(es)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(EntityStoreAuditBuilder.class).principal(principal).type(EventType.ENTITY_STORE_DELETED).throwable(throwable))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete entity store: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete entity store: %s", id), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete entity stores by domain {}", domainId);
        return entityStoreRepository.findByDomain(domainId)
                .flatMapCompletable(es -> entityStoreVersionRepository.deleteByEntityStoreId(es.getId()))
                .andThen(entityStoreRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete entity stores for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete entity stores for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<EntityStoreVersion> getVersions(String entityStoreId) {
        LOGGER.debug("Get versions for entity store: {}", entityStoreId);
        return entityStoreVersionRepository.findByEntityStoreId(entityStoreId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get versions for entity store: {}", entityStoreId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get versions for entity store: %s", entityStoreId), ex));
                });
    }

    @Override
    public Maybe<EntityStoreVersion> getVersion(String entityStoreId, int version) {
        LOGGER.debug("Get version {} for entity store: {}", version, entityStoreId);
        return entityStoreVersionRepository.findByEntityStoreIdAndVersion(entityStoreId, version)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version {} for entity store: {}", version, entityStoreId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version %d for entity store: %s", version, entityStoreId), ex));
                });
    }

    @Override
    public Single<EntityStore> restoreVersion(Domain domain, String id, int version, User principal) {
        LOGGER.debug("Restore entity store {} to version {}", id, version);

        return entityStoreVersionRepository.findByEntityStoreIdAndVersion(id, version)
                .switchIfEmpty(Single.error(new TechnicalManagementException(
                        String.format("Version %d not found for entity store %s", version, id))))
                .flatMap(versionRecord -> {
                    UpdateEntityStore updateRequest = new UpdateEntityStore();
                    updateRequest.setContent(versionRecord.getContent());
                    updateRequest.setCommitMessage("Restore to version " + version);
                    return update(domain, id, updateRequest, principal);
                });
    }
}
