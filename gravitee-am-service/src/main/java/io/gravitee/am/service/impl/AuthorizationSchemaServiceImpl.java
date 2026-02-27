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
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationSchemaService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationSchemaNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationSchema;
import io.gravitee.am.service.model.UpdateAuthorizationSchema;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthorizationSchemaAuditBuilder;
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
public class AuthorizationSchemaServiceImpl implements AuthorizationSchemaService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationSchemaServiceImpl.class);

    private final AuthorizationSchemaRepository authorizationSchemaRepository;
    private final AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository;
    private final AuditService auditService;

    public AuthorizationSchemaServiceImpl(@Lazy AuthorizationSchemaRepository authorizationSchemaRepository,
                                          @Lazy AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository,
                                          AuditService auditService) {
        this.authorizationSchemaRepository = authorizationSchemaRepository;
        this.authorizationSchemaVersionRepository = authorizationSchemaVersionRepository;
        this.auditService = auditService;
    }

    @Override
    public Flowable<AuthorizationSchema> findByDomain(String domainId) {
        LOGGER.debug("Find authorization schemas by domain: {}", domainId);
        return authorizationSchemaRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization schemas by domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization schemas by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationSchema> findById(String id) {
        LOGGER.debug("Find authorization schema by ID: {}", id);
        return authorizationSchemaRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization schema using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization schema using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<AuthorizationSchema> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("Find authorization schema by domain {} and ID: {}", domainId, id);
        return authorizationSchemaRepository.findByDomainAndId(domainId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization schema by domain {} and ID: {}", domainId, id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization schema by domain %s and ID: %s", domainId, id), ex));
                });
    }

    @Override
    public Single<AuthorizationSchema> create(Domain domain, NewAuthorizationSchema request, User principal) {
        LOGGER.debug("Create a new authorization schema {} for domain {}", request, domain.getId());

        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId(RandomString.generate());
        schema.setDomainId(domain.getId());
        schema.setName(request.getName());
        schema.setLatestVersion(1);
        schema.setCreatedAt(new Date());
        schema.setUpdatedAt(schema.getCreatedAt());

        return authorizationSchemaRepository.create(schema)
                .flatMap(created -> {
                    AuthorizationSchemaVersion version = new AuthorizationSchemaVersion();
                    version.setId(RandomString.generate());
                    version.setSchemaId(created.getId());
                    version.setVersion(1);
                    version.setContent(request.getContent());
                    version.setCommitMessage(request.getCommitMessage());
                    version.setCreatedAt(created.getCreatedAt());
                    version.setCreatedBy(principal != null ? principal.getId() : null);

                    return authorizationSchemaVersionRepository.create(version)
                            .map(v -> created);
                })
                .doOnSuccess(s -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_CREATED).authorizationSchema(s)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_CREATED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an authorization schema", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an authorization schema", ex));
                });
    }

    @Override
    public Single<AuthorizationSchema> update(Domain domain, String id, UpdateAuthorizationSchema request, User principal) {
        LOGGER.debug("Update authorization schema {} for domain {}", id, domain.getId());

        return authorizationSchemaRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Single.error(new AuthorizationSchemaNotFoundException(id)))
                .flatMap(existing -> {
                    if (request.getName() != null) {
                        existing.setName(request.getName());
                    }

                    int newVersion = existing.getLatestVersion() + 1;
                    existing.setLatestVersion(newVersion);
                    existing.setUpdatedAt(new Date());

                    return authorizationSchemaRepository.update(existing)
                            .flatMap(updated -> {
                                String content = request.getContent();
                                if (content == null) {
                                    return authorizationSchemaVersionRepository.findLatestBySchemaId(id)
                                            .map(AuthorizationSchemaVersion::getContent)
                                            .defaultIfEmpty("")
                                            .flatMap(prevContent -> createVersionRecord(updated, newVersion, prevContent, request.getCommitMessage(), principal));
                                }
                                return createVersionRecord(updated, newVersion, content, request.getCommitMessage(), principal);
                            })
                            .doOnSuccess(s -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_UPDATED).authorizationSchema(s)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an authorization schema", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an authorization schema", ex));
                });
    }

    private Single<AuthorizationSchema> createVersionRecord(AuthorizationSchema schema, int versionNum, String content, String commitMessage, User principal) {
        AuthorizationSchemaVersion version = new AuthorizationSchemaVersion();
        version.setId(RandomString.generate());
        version.setSchemaId(schema.getId());
        version.setVersion(versionNum);
        version.setContent(content);
        version.setCommitMessage(commitMessage);
        version.setCreatedAt(new Date());
        version.setCreatedBy(principal != null ? principal.getId() : null);

        return authorizationSchemaVersionRepository.create(version)
                .map(v -> schema);
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        LOGGER.debug("Delete authorization schema {}", id);

        return authorizationSchemaRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new AuthorizationSchemaNotFoundException(id)))
                .flatMapCompletable(s -> authorizationSchemaVersionRepository.deleteBySchemaId(id)
                        .andThen(authorizationSchemaRepository.delete(id))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_DELETED).authorizationSchema(s)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationSchemaAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_SCHEMA_DELETED).throwable(throwable))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete authorization schema: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization schema: %s", id), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete authorization schemas by domain {}", domainId);
        return authorizationSchemaRepository.findByDomain(domainId)
                .flatMapCompletable(s -> authorizationSchemaVersionRepository.deleteBySchemaId(s.getId()))
                .andThen(authorizationSchemaRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization schemas for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization schemas for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<AuthorizationSchemaVersion> getVersions(String schemaId) {
        LOGGER.debug("Get versions for authorization schema: {}", schemaId);
        return authorizationSchemaVersionRepository.findBySchemaId(schemaId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get versions for authorization schema: {}", schemaId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get versions for authorization schema: %s", schemaId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationSchemaVersion> getVersion(String schemaId, int version) {
        LOGGER.debug("Get version {} for authorization schema: {}", version, schemaId);
        return authorizationSchemaVersionRepository.findBySchemaIdAndVersion(schemaId, version)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version {} for authorization schema: {}", version, schemaId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version %d for authorization schema: %s", version, schemaId), ex));
                });
    }

    @Override
    public Single<AuthorizationSchema> restoreVersion(Domain domain, String id, int version, User principal) {
        LOGGER.debug("Restore authorization schema {} to version {}", id, version);

        return authorizationSchemaVersionRepository.findBySchemaIdAndVersion(id, version)
                .switchIfEmpty(Single.error(new TechnicalManagementException(
                        String.format("Version %d not found for authorization schema %s", version, id))))
                .flatMap(versionRecord -> {
                    UpdateAuthorizationSchema updateRequest = new UpdateAuthorizationSchema();
                    updateRequest.setContent(versionRecord.getContent());
                    updateRequest.setCommitMessage("Restore to version " + version);
                    return update(domain, id, updateRequest, principal);
                });
    }
}
