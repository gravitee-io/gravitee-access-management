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
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.MalformedIconUriException;
import io.gravitee.am.service.exception.ScopeAlreadyExistsException;
import io.gravitee.am.service.exception.ScopeNotFoundException;
import io.gravitee.am.service.exception.SystemScopeDeleteException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewScope;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.PatchScope;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.am.service.model.UpdateScope;
import io.gravitee.am.service.model.UpdateSystemScope;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ScopeAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class ScopeServiceImpl implements ScopeService {

    @Lazy
    @Autowired
    private ScopeRepository scopeRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Maybe<Scope> findById(String id) {
        log.debug("Find scope by ID: {}", id);
        return scopeRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a scope using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Page<Scope>> search(String domain, String query, int page, int size) {
        log.debug("Search scopes by domain and query: {} {}", domain, query);
        return scopeRepository.search(domain, query, page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find scopes by domain and query : {} {}", domain, query, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain and query: %s %s", domain, query), ex));
                });
    }

    @Override
    public Single<Scope> create(Domain domain, NewScope newScope, User principal) {
        log.debug("Create a new scope {} for domain {}", newScope, domain.getId());
        // replace all whitespace by an underscore (whitespace is a reserved keyword to separate tokens)
        String scopeKey = newScope.getKey().replaceAll("\\s+", "_");
        return scopeRepository.findByDomainAndKey(domain.getId(), scopeKey)
                .isEmpty()
                .map(empty -> {
                    if (!empty) {
                        throw new ScopeAlreadyExistsException(scopeKey, domain.getId());
                    }
                    Scope scope = new Scope();
                    scope.setId(RandomString.generate());
                    scope.setDomain(domain.getId());
                    scope.setKey(scopeKey);
                    scope.setName(newScope.getName());
                    scope.setDescription(newScope.getDescription());
                    scope.setIconUri(newScope.getIconUri());
                    scope.setExpiresIn(newScope.getExpiresIn());
                    scope.setDiscovery(newScope.isDiscovery());
                    scope.setParameterized(newScope.isParameterized());
                    scope.setCreatedAt(new Date());
                    scope.setUpdatedAt(new Date());

                    return scope;
                })
                .flatMap(this::validateIconUri)
                .flatMap(scopeRepository::create)
                .flatMap(scope -> {
                    // create event for sync process
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), ReferenceType.DOMAIN, scope.getDomain(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    log.error("An error occurs while trying to create a scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a scope", ex));
                })
                .doOnSuccess(scope -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_CREATED).scope(scope)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_CREATED).reference(Reference.domain(domain.getId())).throwable(throwable)));
    }

    @Override
    public Single<Scope> create(Domain domain, NewSystemScope newScope) {
        log.debug("Create a new system scope {} for domain {}", newScope, domain.getId());
        String scopeKey = newScope.getKey().toLowerCase();
        return scopeRepository.findByDomainAndKey(domain.getId(), scopeKey)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new ScopeAlreadyExistsException(scopeKey, domain.getId());
                    }
                    Scope scope = new Scope();
                    scope.setId(RandomString.generate());
                    scope.setDomain(domain.getId());
                    scope.setKey(scopeKey);
                    scope.setSystem(true);
                    scope.setClaims(newScope.getClaims());
                    scope.setName(newScope.getName());
                    scope.setDescription(newScope.getDescription());
                    scope.setExpiresIn(newScope.getExpiresIn());
                    scope.setDiscovery(newScope.isDiscovery());
                    scope.setParameterized(false);
                    scope.setCreatedAt(new Date());
                    scope.setUpdatedAt(new Date());
                    return scopeRepository.create(scope);
                })
                .flatMap(scope -> {
                    // create event for sync process
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), ReferenceType.DOMAIN, scope.getDomain(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to create a system scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a system scope", ex));
                });
    }

    @Override
    public Single<Scope> patch(Domain domain, String id, PatchScope patchScope, User principal) {
        log.debug("Patching a scope {} for domain {}", id, domain);
        return scopeRepository.findById(id)
                .switchIfEmpty(Single.error(new ScopeNotFoundException(id)))
                .flatMap(oldScope -> {
                    Scope scopeToUpdate = patchScope.patch(oldScope);
                    return update(domain, scopeToUpdate, oldScope, principal);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to patch a scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to patch a scope", ex));
                });
    }

    @Override
    public Single<Scope> update(Domain domain, String id, UpdateScope updateScope, User principal) {
        log.debug("Update a scope {} for domain {}", id, domain.getId());
        return scopeRepository.findById(id)
                .switchIfEmpty(Single.error(new ScopeNotFoundException(id)))
                .flatMap(oldScope -> {
                    Scope scopeToUpdate = new Scope(oldScope);
                    scopeToUpdate.setName(updateScope.getName());
                    scopeToUpdate.setDescription(updateScope.getDescription());
                    scopeToUpdate.setExpiresIn(updateScope.getExpiresIn());
                    if (!oldScope.isSystem() && updateScope.getDiscovery() != null) {
                        scopeToUpdate.setDiscovery(updateScope.isDiscovery());
                    }
                    if (!oldScope.isSystem() && updateScope.getParameterized() != null) {
                        scopeToUpdate.setParameterized(updateScope.isParameterized());
                    }
                    scopeToUpdate.setIconUri(updateScope.getIconUri());

                    return update(domain, scopeToUpdate, oldScope, principal);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to update a scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a scope", ex));
                });
    }

    private Single<Scope> update(Domain domain, Scope toUpdate, Scope oldValue, User principal) {

        toUpdate.setUpdatedAt(new Date());
        return this.validateIconUri(toUpdate)
                .flatMap(scopeRepository::update)
                .flatMap(scope1 -> {
                    // create event for sync process
                    Event event = new Event(Type.SCOPE, new Payload(scope1.getId(), ReferenceType.DOMAIN, scope1.getDomain(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(scope1));
                })
                .doOnSuccess(scope1 -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_UPDATED).oldValue(oldValue).scope(scope1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_UPDATED).reference(Reference.domain(domain.getId())).throwable(throwable)));
    }

    @Override
    public Single<Scope> update(Domain domain, String id, UpdateSystemScope updateScope) {
        log.debug("Update a system scope {} for domain {}", id, domain.getId());
        return scopeRepository.findById(id)
                .switchIfEmpty(Single.error(new ScopeNotFoundException(id)))
                .flatMap(scope -> {
                    scope.setName(updateScope.getName());
                    scope.setDescription(updateScope.getDescription());
                    scope.setUpdatedAt(new Date());
                    scope.setSystem(true);
                    scope.setClaims(updateScope.getClaims());
                    scope.setExpiresIn(updateScope.getExpiresIn());
                    scope.setDiscovery(updateScope.isDiscovery());
                    return scopeRepository.update(scope);
                })
                .flatMap(scope -> {
                    // create event for sync process
                    Event event = new Event(Type.SCOPE, new Payload(scope.getId(), ReferenceType.DOMAIN, scope.getDomain(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(scope));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to update a system scope", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a system scope", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String scopeId, boolean force, User principal) {
        log.debug("Delete scope {}", scopeId);
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
                                                .filter(role -> role.getOauthScopes() != null && role.getOauthScopes().contains(scope.getKey()))
                                                .collect(Collectors.toList())))
                                        .flatMapSingle(role -> {
                                            role.getOauthScopes().remove(scope.getKey());
                                            UpdateRole updatedRole = new UpdateRole();
                                            updatedRole.setName(role.getName());
                                            updatedRole.setDescription(role.getDescription());
                                            updatedRole.setPermissions(role.getOauthScopes());
                                            // Save role
                                            return roleService.update(scope.getDomain(), role.getId(), updatedRole);
                                        }).toList())
                                .andThen(
                                        // 2_ Remove scopes from application
                                        Completable.fromSingle(applicationService.findByDomain(scope.getDomain())
                                                .flatMapObservable(applications -> Observable.fromIterable(applications.stream()
                                                        .filter(application -> {
                                                            if (application.getSettings() == null) {
                                                                return false;
                                                            }
                                                            if (application.getSettings().getOauth() == null) {
                                                                return false;
                                                            }
                                                            ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
                                                            return oAuthSettings.getScopeSettings() != null && !oAuthSettings.getScopeSettings().stream().filter(s -> s.getScope().equals(scope.getKey())).findFirst().isEmpty();
                                                        })
                                                        .collect(Collectors.toList())))
                                                .flatMapSingle(application -> {
                                                    // Remove scope from application
                                                    final List<ApplicationScopeSettings> cleanScopes = application.getSettings().getOauth().getScopeSettings().stream().filter(s -> !s.getScope().equals(scope.getKey())).collect(Collectors.toList());
                                                    application.getSettings().getOauth().setScopeSettings(cleanScopes);
                                                    // Then update
                                                    return applicationService.update(application);
                                                }).toList()))
                                // 3_ Remove scopes from scope_approvals
                                .andThen(dataPlaneRegistry.getScopeApprovalRepository(domain).deleteByDomainAndScopeKey(scope.getDomain(), scope.getKey()))
                                // 4_ Delete scope
                                .andThen(scopeRepository.delete(scopeId))
                                // 5_ create event for sync process
                                .andThen(
                                        Completable.fromSingle(
                                                eventService.create(new Event(Type.SCOPE, new Payload(scope.getId(), ReferenceType.DOMAIN, scope.getDomain(), Action.DELETE)), domain))
                                )
                                .doOnComplete(() -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_DELETED).scope(scope)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ScopeAuditBuilder.class).principal(principal).type(EventType.SCOPE_DELETED).reference(Reference.domain(scope.getDomain())).throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    log.error("An error occurs while trying to delete scope: {}", scopeId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete scope: %s", scopeId), ex));
                });
    }

    @Override
    public Single<Page<Scope>> findByDomain(String domain, int page, int size) {
        log.debug("Find scopes by domain: {}", domain);
        return scopeRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find scopes by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain: %s", domain), ex));
                });
    }

    @Override
    public Maybe<Scope> findByDomainAndKey(String domain, String scopeKey) {
        log.debug("Find scopes by domain: {} and scope key: {}", domain, scopeKey);
        return scopeRepository.findByDomainAndKey(domain, scopeKey)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find scopes by domain: {} and scope key: {}", domain, scopeKey, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain: %s and scope key: %s", domain, scopeKey), ex));
                });
    }

    @Override
    public Single<List<Scope>> findByDomainAndKeys(String domain, List<String> scopeKeys) {
        log.debug("Find scopes by domain: {} and scope keys: {}", domain, scopeKeys);
        if(scopeKeys==null || scopeKeys.isEmpty()) {
            return Single.just(Collections.emptyList());
        }
        return scopeRepository.findByDomainAndKeys(domain, scopeKeys).toList()
                .onErrorResumeNext(ex -> {
                    String keys = scopeKeys!=null?String.join(",",scopeKeys):null;
                    log.error("An error occurs while trying to find scopes by domain: {} and scope keys: {}", domain, keys, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find scopes by domain: %s and scope keys: %s", domain, keys), ex));
                });
    }

    /**
     * Throw InvalidClientMetadataException if null or empty, or contains unknown scope.
     * @param scopes Array of scope to validate.
     */
    @Override
    public Single<Boolean> validateScope(String domain, List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Single.just(true);//nothing to do...
        }

        return findByDomain(domain, 0, Integer.MAX_VALUE)
                .map(domainSet -> domainSet.getData().stream().map(Scope::getKey).collect(Collectors.toSet()))
                .flatMap(domainScopes -> this.validateScope(domainScopes, scopes));
    }

    private Single<Boolean> validateScope(Set<String> domainScopes, List<String> scopes) {

        for (String scope : scopes) {
            if (!domainScopes.contains(scope)) {
                return Single.error(new InvalidClientMetadataException("scope " + scope + " is not valid."));
            }
        }

        return Single.just(true);
    }

    private Single<Scope> validateIconUri(Scope scope) {
        if(scope.getIconUri()!=null) {
            try {
                URI.create(scope.getIconUri()).toURL();
            } catch (MalformedURLException | IllegalArgumentException e) {
                return Single.error(new MalformedIconUriException(scope.getIconUri()));
            }
        }
        return Single.just(scope);
    }
}
