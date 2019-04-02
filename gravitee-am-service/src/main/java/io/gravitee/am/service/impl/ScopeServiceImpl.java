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
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.*;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ScopeAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class ScopeServiceImpl implements ScopeService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ScopeServiceImpl.class);

    @Autowired
    private ScopeRepository scopeRepository;

    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<Scope> findById(String id) {
        LOGGER.debug("Find scope by ID: {}", id);
        return scopeRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Scope> create(String domain, NewScope newScope, User principal) {
        LOGGER.debug("Create a new scope {} for domain {}", newScope, domain);
        String scopeKey = newScope.getKey().toLowerCase();
        return scopeRepository.findByDomainAndKey(domain, scopeKey)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new ScopeAlreadyExistsException(scopeKey, domain);
                    }
                    Scope scope = new Scope();
                    scope.setId(RandomString.generate());
                    scope.setDomain(domain);
                    scope.setKey(scopeKey);
                    scope.setName(newScope.getName());
                    scope.setDescription(newScope.getDescription());
                    scope.setExpiresIn(newScope.getExpiresIn());
                    scope.setCreatedAt(new Date());
                    scope.setUpdatedAt(new Date());

                    return scopeRepository.create(scope);
                })
                .flatMap(scope -> {
                    // Reload domain to take care about scope creation
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), scope.getDomain(), Action.CREATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a scope", ex));
                })
                .doOnSuccess(scope -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_CREATED).scope(scope)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Scope> create(String domain, NewSystemScope newScope) {
        LOGGER.debug("Create a new system scope {} for domain {}", newScope, domain);
        String scopeKey = newScope.getKey().toLowerCase();
        return scopeRepository.findByDomainAndKey(domain, scopeKey)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new ScopeAlreadyExistsException(scopeKey, domain);
                    }
                    Scope scope = new Scope();
                    scope.setId(RandomString.generate());
                    scope.setDomain(domain);
                    scope.setKey(scopeKey);
                    scope.setSystem(true);
                    scope.setClaims(newScope.getClaims());
                    scope.setName(newScope.getName());
                    scope.setDescription(newScope.getDescription());
                    scope.setExpiresIn(newScope.getExpiresIn());
                    scope.setCreatedAt(new Date());
                    scope.setUpdatedAt(new Date());
                    return scopeRepository.create(scope);
                })
                .flatMap(scope -> {
                    // Reload domain to take care about scope creation
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), scope.getDomain(), Action.CREATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a system scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a system scope", ex));
                });
    }

    @Override
    public Single<Scope> update(String domain, String id, UpdateScope updateScope, User principal) {
        LOGGER.debug("Update a scope {} for domain {}", id, domain);
        return scopeRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ScopeNotFoundException(id)))
                .flatMapSingle(oldScope -> {
                    Scope scopeToUpdate = new Scope(oldScope);
                    scopeToUpdate.setName(updateScope.getName());
                    scopeToUpdate.setDescription(updateScope.getDescription());
                    scopeToUpdate.setExpiresIn(updateScope.getExpiresIn());
                    scopeToUpdate.setUpdatedAt(new Date());

                    return scopeRepository.update(scopeToUpdate)
                            .flatMap(scope1 -> {
                                // Reload domain to take care about scope update
                                Event event = new Event(Type.SCOPE, new Payload(scope1.getId(), scope1.getDomain(), Action.UPDATE));
                                return domainService.reload(domain, event).flatMap(domain1 -> Single.just(scope1));
                            })
                            .doOnSuccess(scope1 -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_UPDATED).oldValue(oldScope).scope(scope1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a scope", ex));
                });
    }

    @Override
    public Single<Scope> update(String domain, String id, UpdateSystemScope updateScope) {
        LOGGER.debug("Update a system scope {} for domain {}", id, domain);
        return scopeRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ScopeNotFoundException(id)))
                .flatMapSingle(scope -> {
                    scope.setName(updateScope.getName());
                    scope.setDescription(updateScope.getDescription());
                    scope.setUpdatedAt(new Date());
                    scope.setSystem(true);
                    scope.setClaims(updateScope.getClaims());
                    scope.setExpiresIn(updateScope.getExpiresIn());
                    return scopeRepository.update(scope);
                })
                .flatMap(scope -> {
                    // Reload domain to take care about scope update
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), scope.getDomain(), Action.UPDATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a system scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a system scope", ex));
                });
    }

    @Override
    public Completable delete(String scopeId, boolean force, User principal) {
        LOGGER.debug("Delete scope {}", scopeId);
        return scopeRepository.findById(scopeId)
                .switchIfEmpty(Maybe.error(new ScopeNotFoundException(scopeId)))
                .flatMapSingle(scope -> {
                    if (scope.isSystem() && !force) {
                        throw new SystemScopeDeleteException(scopeId);
                    }
                    return Single.just(scope);
                })
                .flatMapCompletable(scope ->

                        Completable.fromSingle(
                                // 1_ Remove permissions from role
                                roleService.findByDomain(scope.getDomain())
                                        .flatMapObservable(roles -> Observable.fromIterable(roles.stream()
                                                .filter(role -> role.getPermissions() != null && role.getPermissions().contains(scope.getKey()))
                                                .collect(Collectors.toList())))
                                        .flatMapSingle(role -> {
                                            role.getPermissions().remove(scope.getKey());
                                            UpdateRole updatedRole = new UpdateRole();
                                            updatedRole.setName(role.getName());
                                            updatedRole.setDescription(role.getDescription());
                                            updatedRole.setPermissions(role.getPermissions());
                                            // Save role
                                            return roleService.update(scope.getDomain(), role.getId(), updatedRole);
                                        }).toList())
                                .andThen(
                                        // 2_ Remove scopes from client
                                        clientService.findByDomain(scope.getDomain())
                                                .flatMapObservable(clients -> Observable.fromIterable(clients.stream()
                                                        .filter(client -> client.getScopes() != null && client.getScopes().contains(scope.getKey()))
                                                        .collect(Collectors.toList())))
                                                .flatMapSingle(client -> {
                                                    // Remove scope from client
                                                    client.getScopes().remove(scope.getKey());
                                                    // Then patch
                                                    PatchClient patchClient = new PatchClient();
                                                    patchClient.setScopes(Optional.of(client.getScopes()));
                                                    return clientService.patch(scope.getDomain(), client.getId(), patchClient);
                                                }).toList()).toCompletable()
                                // 3_ Remove scopes from scope_approvals
                                .andThen(scopeApprovalRepository.deleteByDomainAndScopeKey(scope.getDomain(), scope.getKey()))
                                // 4_ Delete scope
                                .andThen(scopeRepository.delete(scopeId))
                                // 5_ reload domain
                                .andThen(
                                        Completable.fromSingle(
                                                domainService.reload(scope.getDomain(),
                                                        new Event(Type.SCOPE, new Payload(scope.getId(), scope.getDomain(), Action.DELETE))))
                                )
                                .doOnComplete(() -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_DELETED).scope(scope)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_DELETED).throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete scope: {}", scopeId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete scope: %s", scopeId), ex));
                });
    }

    @Override
    public Single<Set<Scope>> findByDomain(String domain) {
        LOGGER.debug("Find scopes by domain: {}", domain);
        return scopeRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find scopes by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain: %s", domain), ex));
                });
    }

    @Override
    public Maybe<Scope> findByDomainAndKey(String domain, String scopeKey) {
        LOGGER.debug("Find scopes by domain: {} and scope key: {}", domain, scopeKey);
        return scopeRepository.findByDomainAndKey(domain, scopeKey)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find scopes by domain: {} and scope key: {}", domain, scopeKey, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain: %s and scope key: %s", domain, scopeKey), ex));
                });
    }

    /**
     * Throw InvalidClientMetadataException if null or empty, or contains unknown scope.
     * @param scopes Array of scope to validate.
     */
    @Override
    public Single<Boolean> validateScope(String domain, List<String> scopes) {
        if(scopes==null || scopes.isEmpty()) {
            return Single.just(true);//nothing to do...
        }

        return findByDomain(domain)
                .map(domainSet -> domainSet.stream().map(scope -> scope.getKey()).collect(Collectors.toSet()))
                .flatMap(domainScopes -> this.validateScope(domainScopes,scopes));
    }

    private Single<Boolean> validateScope(Set<String> domainScopes, List<String> scopes) {

        for(String scope:scopes) {
            if(!domainScopes.contains(scope)) {
                return Single.error(new InvalidClientMetadataException("scope "+scope+" is not valid."));
            }
        }

        return Single.just(true);
    }


}
