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
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.permissions.*;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.RoleAlreadyExistsException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.RoleAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RoleServiceImpl implements RoleService {

    private final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Override
    public Single<Set<Role>> findByDomain(String domain) {
        LOGGER.debug("Find roles by domain: {}", domain);
        return roleRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find roles by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find roles by domain", ex));
                });
    }

    @Override
    public Maybe<Role> findById(String id) {
        LOGGER.debug("Find role by ID: {}", id);
        return roleRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a role using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> ids) {
        LOGGER.debug("Find roles by ids: {}", ids);
        return roleRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find roles by ids", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find roles by ids", ex));
                });
    }


    @Override
    public Single<Role> create(String domain, NewRole newRole, User principal) {
        LOGGER.debug("Create a new role {} for domain {}", newRole, domain);

        String roleId = RandomString.generate();

        // check if role name is unique
        return checkRoleUniqueness(newRole.getName(), roleId, domain)
                .flatMap(__ -> {
                    Role role = new Role();
                    role.setId(roleId);
                    role.setDomain(domain);
                    role.setName(newRole.getName());
                    role.setDescription(newRole.getDescription());
                    role.setScope(newRole.getScope() != null ? newRole.getScope().getId() : null);
                    role.setCreatedAt(new Date());
                    role.setUpdatedAt(role.getCreatedAt());
                    return roleRepository.create(role);
                })
                // create event for sync process
                .flatMap(role -> {
                    Event event = new Event(Type.ROLE, new Payload(role.getId(), role.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(role));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a role", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a role", ex));
                })
                .doOnSuccess(role -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_CREATED).role(role)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Role> update(String domain, String id, UpdateRole updateRole, User principal) {
        LOGGER.debug("Update a role {} for domain {}", id, domain);

        return roleRepository.findById(id)
                .switchIfEmpty(Maybe.error(new RoleNotFoundException(id)))
                .flatMapSingle(oldRole -> {
                    // check if role name is unique
                    return checkRoleUniqueness(updateRole.getName(), oldRole.getId(), domain)
                            .flatMap(irrelevant -> {
                                Role roleToUpdate = new Role(oldRole);
                                roleToUpdate.setName(updateRole.getName());
                                roleToUpdate.setDescription(updateRole.getDescription());
                                roleToUpdate.setPermissions(updateRole.getPermissions());
                                roleToUpdate.setUpdatedAt(new Date());
                                return roleRepository.update(roleToUpdate)
                                        // create event for sync process
                                        .flatMap(role -> {
                                            Event event = new Event(Type.ROLE, new Payload(role.getId(), role.getDomain(), Action.UPDATE));
                                            return eventService.create(event).flatMap(__ -> Single.just(role));
                                        })
                                        .doOnSuccess(role -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_UPDATED).oldValue(oldRole).role(role)))
                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_UPDATED).throwable(throwable)));
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a role", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a role", ex));
                });

    }

    @Override
    public Completable delete(String roleId, User principal) {
        LOGGER.debug("Delete role {}", roleId);
        return roleRepository.findById(roleId)
                .switchIfEmpty(Maybe.error(new RoleNotFoundException(roleId)))
                .flatMapCompletable(role -> roleRepository.delete(roleId)
                        .andThen(Completable.fromSingle(eventService.create(new Event(Type.ROLE, new Payload(role.getId(), role.getDomain(), Action.DELETE)))))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_DELETED).role(role)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).principal(principal).type(EventType.ROLE_DELETED).throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete role: {}", roleId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete role: %s", roleId), ex));
                });
    }

    @Override
    public Single<Role> createSystemRole(SystemRole systemRole, RoleScope roleScope, List<String> permissions, User principal) {
        Role role = initSystemRole(systemRole.name(), roleScope.getId(), permissions);
        return roleRepository.create(role)
                .flatMap(role1 -> {
                    Event event = new Event(Type.ROLE, new Payload(role1.getId(), role1.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(role1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a system role {}", RoleScope.valueOf(role.getScope()) + ":" + role.getName(), ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a role", ex));
                })
                .doOnSuccess(role1 -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).role(role1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).throwable(throwable)));
    }

    @Override
    public Completable createOrUpdateSystemRoles() {
        // MANAGEMENT - ADMIN
        Role managementAdmin = initSystemRole(SystemRole.ADMIN.name(), RoleScope.MANAGEMENT.getId(), ManagementPermission.permissions());
        // DOMAIN  - PRIMARY_OWNER
        Role domainPrimaryOwner = initSystemRole(SystemRole.PRIMARY_OWNER.name(), RoleScope.DOMAIN.getId(), DomainPermission.permissions());
        // APPLICATION  - PRIMARY_OWNER
        Role applicationPrimaryOwner = initSystemRole(SystemRole.PRIMARY_OWNER.name(), RoleScope.APPLICATION.getId(), DomainPermission.permissions());

        return Observable.fromIterable(Arrays.asList(managementAdmin, domainPrimaryOwner, applicationPrimaryOwner))
                .flatMapCompletable(this::upsert);
    }

    private Completable upsert(Role role) {
        return roleRepository.findByDomainAndNameAndScope(role.getDomain(), role.getName(), role.getScope())
                .map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(optRole -> {
                    if (!optRole.isPresent()) {
                        LOGGER.debug("Create a system role {}", RoleScope.valueOf(role.getScope()) + ":" + role.getName());
                        role.setCreatedAt(new Date());
                        role.setUpdatedAt(role.getCreatedAt());
                        return roleRepository.create(role)
                                .flatMap(role1 -> {
                                    Event event = new Event(Type.ROLE, new Payload(role1.getId(), role1.getDomain(), Action.CREATE));
                                    return eventService.create(event).flatMap(__ -> Single.just(role1));
                                })
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof AbstractManagementException) {
                                        return Single.error(ex);
                                    }
                                    LOGGER.error("An error occurs while trying to create a system role {}", RoleScope.valueOf(role.getScope()) + ":" + role.getName(), ex);
                                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a role", ex));
                                })
                                .doOnSuccess(role1 -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).role(role1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).throwable(throwable)))
                                .toCompletable();

                    } else {
                        // check if permission set has changed
                        Role currentRole = optRole.get();
                        if (!permissionsAreDifferent(currentRole, role)) {
                            return Completable.complete();
                        }
                        LOGGER.debug("Update a system role {}", RoleScope.valueOf(role.getScope()) + ":" + role.getName());
                        // update the role
                        role.setId(currentRole.getId());
                        role.setPermissions(role.getPermissions());
                        role.setUpdatedAt(new Date());
                        return roleRepository.update(role)
                                .flatMap(role1 -> {
                                    Event event = new Event(Type.ROLE, new Payload(role1.getId(), role1.getDomain(), Action.UPDATE));
                                    return eventService.create(event).flatMap(__ -> Single.just(role1));
                                })
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof AbstractManagementException) {
                                        return Single.error(ex);
                                    }
                                    LOGGER.error("An error occurs while trying to update a system role {}", RoleScope.valueOf(role.getScope()) + ":" + role.getName(), ex);
                                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a role", ex));
                                })
                                .doOnSuccess(role1 -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_UPDATED).oldValue(currentRole).role(role1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_UPDATED).throwable(throwable)))
                                .toCompletable();
                    }
                });

    }

    private Role initSystemRole(String roleName, int roleScope, List<String> permissions) {
        Role role = new Role();
        role.setSystem(true);
        role.setDomain("admin");
        role.setName(roleName);
        role.setDescription("System Role. Created by Gravitee.io");
        role.setScope(roleScope);
        // set permissions
        List<String> perms = new ArrayList<>();
        permissions.forEach(p -> RolePermissionAction.actions().forEach(a -> perms.add(p + "_" + a)));
        role.setPermissions(perms);
        return role;
    }

    private Single<Set<Role>> checkRoleUniqueness(String roleName, String roleId, String domain) {
        return roleRepository.findByDomain(domain)
                .flatMap(roles -> {
                    if (roles.stream()
                            .filter(role -> !role.getId().equals(roleId))
                            .anyMatch(role -> role.getName().equals(roleName))) {
                        throw new RoleAlreadyExistsException(roleName);
                    }
                    return Single.just(roles);
                });
    }

    private boolean permissionsAreDifferent(Role role1, Role role2) {
        if (role1.getPermissions().size() != role2.getPermissions().size()) {
            return true;
        } else {
            ArrayList<String> listOne = new ArrayList<>(role1.getPermissions());
            ArrayList<String> listTwo = new ArrayList<>(role2.getPermissions());

            //remove all elements from second list
            listOne.removeAll(listTwo);
            return !listOne.isEmpty();
        }
    }


}
