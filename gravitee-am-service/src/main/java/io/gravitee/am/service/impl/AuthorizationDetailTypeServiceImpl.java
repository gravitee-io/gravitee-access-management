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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetailTypeCursorRequest;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationDetailTypeService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationDetailTypeAlreadyExistsException;
import io.gravitee.am.service.exception.AuthorizationDetailTypeNotFoundException;
import io.gravitee.am.service.exception.InvalidAuthorizationDetailTypeException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationDetailType;
import io.gravitee.am.service.model.PatchAuthorizationDetailType;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthorizationDetailTypeAuditBuilder;
import io.gravitee.am.service.validators.authorizationdetailtype.AuthorizationDetailTypeValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@CustomLog
@Component
public class AuthorizationDetailTypeServiceImpl implements AuthorizationDetailTypeService {

    @Lazy
    @Autowired
    private AuthorizationDetailTypeRepository repository;

    @Autowired
    private AuthorizationDetailTypeValidator validator;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<AuthorizationDetailType> findById(String domain, String id) {
        log.debug("Find authorization detail type by domain and ID: {} {}", domain, id);
        return repository.findByDomainAndId(domain, id)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurred while trying to find an authorization detail type using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurred while trying to find an authorization detail type using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest>> findByDomainCursor(String domain, AuthorizationDetailTypeCursorRequest cursor, int limit) {
        log.debug("Find authorization detail types by domain: {}", domain);
        return repository.findByDomainCursor(domain, cursor, limit)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurred while trying to find the authorization detail types of domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurred while trying to find the authorization detail types of domain %s", domain), ex));
                });
    }

    @Override
    public Single<AuthorizationDetailType> create(Domain domain, NewAuthorizationDetailType newAuthorizationDetailType, User principal) {
        log.debug("Create a new authorization detail type {} for domain {}", newAuthorizationDetailType, domain.getId());
        Date now = new Date();
        AuthorizationDetailType toCreate = AuthorizationDetailType.builder()
                .id(RandomString.generate())
                .domain(domain.getId())
                .type(newAuthorizationDetailType.getType())
                .name(newAuthorizationDetailType.getName())
                .description(newAuthorizationDetailType.getDescription())
                .schema(newAuthorizationDetailType.getSchema())
                .discovery(newAuthorizationDetailType.isDiscovery())
                .expiresIn(newAuthorizationDetailType.getExpiresIn())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return validate(toCreate)
                .andThen(Single.defer(() -> repository.findByDomainAndType(domain.getId(), toCreate.getType()).isEmpty()))
                .flatMap(empty -> {
                    if (!empty) {
                        return Single.error(new AuthorizationDetailTypeAlreadyExistsException(toCreate.getType(), domain.getId()));
                    }
                    return repository.create(toCreate);
                })
                .flatMap(created -> {
                    Event event = new Event(Type.AUTHORIZATION_DETAIL_TYPE, new Payload(created.getId(), ReferenceType.DOMAIN, created.getDomain(), Action.CREATE));
                    return eventService.create(event, domain).map(__ -> created);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    if (ex instanceof UniqueConstraintViolationException) {
                        return Single.error(new AuthorizationDetailTypeAlreadyExistsException(toCreate.getType(), domain.getId()));
                    }
                    log.error("An error occurred while trying to create an authorization detail type", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to create an authorization detail type", ex));
                })
                .doOnSuccess(created -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_DETAIL_TYPE_CREATED)
                        .authorizationDetailType(created)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_DETAIL_TYPE_CREATED)
                        .reference(Reference.domain(domain.getId()))
                        .throwable(throwable)));
    }

    @Override
    public Single<AuthorizationDetailType> patch(Domain domain, String id, PatchAuthorizationDetailType patchAuthorizationDetailType, User principal) {
        log.debug("Patch authorization detail type {} for domain {}", id, domain.getId());
        return findById(domain.getId(), id)
                .switchIfEmpty(Single.error(new AuthorizationDetailTypeNotFoundException(id)))
                .flatMap(existing -> {
                    AuthorizationDetailType toUpdate = patchAuthorizationDetailType.patch(existing);
                    toUpdate.setUpdatedAt(new Date());
                    return update(domain, existing, toUpdate, principal);
                });
    }

    private Single<AuthorizationDetailType> update(Domain domain, AuthorizationDetailType oldValue, AuthorizationDetailType toUpdate, User principal) {
        return validate(toUpdate)
                .andThen(Single.defer(() -> repository.update(toUpdate)))
                .flatMap(updated -> {
                    Event event = new Event(Type.AUTHORIZATION_DETAIL_TYPE, new Payload(updated.getId(), ReferenceType.DOMAIN, updated.getDomain(), Action.UPDATE));
                    return eventService.create(event, domain).map(__ -> updated);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurred while trying to update an authorization detail type", ex);
                    return Single.error(new TechnicalManagementException("An error occurred while trying to update an authorization detail type", ex));
                })
                .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_DETAIL_TYPE_UPDATED)
                        .oldValue(oldValue)
                        .authorizationDetailType(updated)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_DETAIL_TYPE_UPDATED)
                        .reference(Reference.domain(domain.getId()))
                        .throwable(throwable)));
    }

    private Completable validate(AuthorizationDetailType authorizationDetailType) {
        return Completable.defer(() -> validator.validate(authorizationDetailType)
                .map(error -> Completable.error(new InvalidAuthorizationDetailTypeException(error)))
                .orElse(Completable.complete()));
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        log.debug("Delete authorization detail type {} for domain {}", id, domain.getId());
        return findById(domain.getId(), id)
                .switchIfEmpty(Single.error(new AuthorizationDetailTypeNotFoundException(id)))
                .flatMapCompletable(existing -> repository.delete(existing.getId())
                        .andThen(Completable.defer(() -> {
                            Event event = new Event(Type.AUTHORIZATION_DETAIL_TYPE, new Payload(existing.getId(), ReferenceType.DOMAIN, existing.getDomain(), Action.DELETE));
                            return eventService.create(event, domain).ignoreElement();
                        }))
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof AbstractManagementException) {
                                return Completable.error(ex);
                            }
                            log.error("An error occurred while trying to delete the authorization detail type {}", id, ex);
                            return Completable.error(new TechnicalManagementException(
                                    String.format("An error occurred while trying to delete the authorization detail type %s", id), ex));
                        })
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.AUTHORIZATION_DETAIL_TYPE_DELETED)
                                .authorizationDetailType(existing)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationDetailTypeAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.AUTHORIZATION_DETAIL_TYPE_DELETED)
                                .reference(Reference.domain(domain.getId()))
                                .throwable(throwable))));
    }

    @Override
    public Completable deleteByDomain(String domain) {
        log.debug("Delete every authorization detail type of domain {}", domain);
        return repository.deleteByDomain(domain)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurred while trying to delete the authorization detail types of domain {}", domain, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurred while trying to delete the authorization detail types of domain %s", domain), ex));
                });
    }
}
