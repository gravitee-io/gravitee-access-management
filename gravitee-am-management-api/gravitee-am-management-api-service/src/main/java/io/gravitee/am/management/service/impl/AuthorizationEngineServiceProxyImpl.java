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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.AuthorizationEnginePluginService;
import io.gravitee.am.management.service.AuthorizationEngineServiceProxy;
import io.gravitee.am.management.service.exception.AuthorizationEnginePluginSchemaNotFoundException;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthorizationEngineAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;


/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationEngineServiceProxyImpl extends AbstractSensitiveProxy implements AuthorizationEngineServiceProxy {

    private final AuthorizationEngineService service;
    public AuthorizationEngineServiceProxyImpl(AuthorizationEngineService service,
                                               AuthorizationEnginePluginService pluginService,
                                               AuditService auditService,
                                               ObjectMapper objectMapper) {
        this.service = service;
        this.pluginService = pluginService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Maybe<AuthorizationEngine> findById(String id) {
        return service.findById(id).flatMap(engine -> this.filterSensitiveData(engine).toMaybe());
    }

    @Override
    public Flowable<AuthorizationEngine> findAll() {
        return service.findAll().flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Flowable<AuthorizationEngine> findByDomain(String domain) {
        return service.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<AuthorizationEngine> create(String domainId, NewAuthorizationEngine newAuthorizationEngine, User principal) {
        return service.create(domainId, newAuthorizationEngine, principal)
                .flatMap(this::filterSensitiveDataForCreate)
                .doOnSuccess(createdEngine -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_ENGINE_CREATED)
                        .authorizationEngine(createdEngine)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_ENGINE_CREATED)
                        .reference(new Reference(ReferenceType.DOMAIN, domainId))
                        .throwable(throwable)));
    }

    @Override
    public Single<AuthorizationEngine> update(String domainId, String id, UpdateAuthorizationEngine updateAuthorizationEngine, User principal) {
        return service.findById(id)
                .switchIfEmpty(Single.error(new AuthorizationEngineNotFoundException(id)))
                .flatMap(oldEngine -> filterSensitiveData(oldEngine)
                        .flatMap(safeOldEngine ->
                                updateSensitiveData(updateAuthorizationEngine, oldEngine)
                                    .flatMap(engineToUpdate -> service.update(domainId, id, engineToUpdate, principal))
                                    .flatMap(this::filterSensitiveData)
                                    .doOnSuccess(updatedEngine -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                            .principal(principal)
                                            .type(EventType.AUTHORIZATION_ENGINE_UPDATED)
                                            .oldValue(safeOldEngine)
                                            .authorizationEngine(updatedEngine)))
                                    .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                            .principal(principal)
                                            .type(EventType.AUTHORIZATION_ENGINE_UPDATED)
                                            .reference(new Reference(safeOldEngine.getReferenceType(), safeOldEngine.getReferenceId()))
                                            .authorizationEngine(safeOldEngine)
                                            .throwable(throwable)))
                        )
                );
    }

    @Override
    public Completable delete(String domainId, String authorizationEngineId, User principal) {
        return service.findById(authorizationEngineId)
                .switchIfEmpty(Maybe.error(new AuthorizationEngineNotFoundException(authorizationEngineId)))
                .flatMapCompletable(authorizationEngine ->
                        service.delete(domainId, authorizationEngineId, principal)
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_DELETED)
                                    .authorizationEngine(authorizationEngine)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_DELETED)
                                    .reference(new Reference(ReferenceType.DOMAIN, domainId))
                                    .authorizationEngine(authorizationEngine)
                                    .throwable(throwable)))
                );
    }

    @Override
    protected RuntimeException getSchemaNotFoundException(String type) {
        return new AuthorizationEnginePluginSchemaNotFoundException(type);
    }
}
