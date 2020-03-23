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
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.permissions.*;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.RoleAuditBuilder;
import io.reactivex.*;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.model.Acl.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RoleServiceImpl implements RoleService {

    private final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);

    @Lazy
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Override
    public Flowable<Role> findAllAssignable(ReferenceType referenceType, String referenceId, ReferenceType assignableType) {
        LOGGER.debug("Find roles by {}: {} assignable to {}", referenceType, referenceId, assignableType);

        // Organization roles must be zipped with system roles to get a complete list of all roles.
        return Flowable.merge(findAllSystem(assignableType), roleRepository.findAll(referenceType, referenceId))
                .filter(role -> assignableType == null || assignableType == role.getAssignableType())
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find roles by {}: {} assignable to {}", referenceType, referenceId, assignableType, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find roles by %s %s assignable to %s", referenceType, referenceId, assignableType), ex));
                });
    }

    @Override
    public Single<Set<Role>> findByDomain(String domain) {
        return roleRepository.findAll(ReferenceType.DOMAIN, domain)
                .collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Role> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find role by ID: {}", id);

        return roleRepository.findById(referenceType, referenceId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a role using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its ID: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new RoleNotFoundException(id)));
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
    public Maybe<Role> findSystemRole(SystemRole systemRole, ReferenceType assignableType) {
        LOGGER.debug("Find system role : {} for the scope : {}", systemRole.name(), assignableType);
        return roleRepository.findByNameAndAssignableType(ReferenceType.PLATFORM, Platform.DEFAULT, systemRole.name(), assignableType)
                .filter(Role::isSystem)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find system role : {} for type : {}", systemRole.name(), assignableType, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find system role : %s for type : %s", systemRole.name(), assignableType), ex));
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
    public Single<Role> create(ReferenceType referenceType, String referenceId, NewRole newRole, User principal) {
        LOGGER.debug("Create a new role {} for {} {}", newRole, referenceType, referenceId);

        String roleId = RandomString.generate();

        // check if role name is unique
        return checkRoleUniqueness(newRole.getName(), roleId, referenceType, referenceId)
                .flatMap(__ -> {
                    Role role = new Role();
                    role.setId(roleId);
                    role.setReferenceType(referenceType);
                    role.setReferenceId(referenceId);
                    role.setName(newRole.getName());
                    role.setDescription(newRole.getDescription());
                    role.setAssignableType(newRole.getAssignableType());
                    role.setCreatedAt(new Date());
                    role.setUpdatedAt(role.getCreatedAt());
                    return roleRepository.create(role);
                })
                // create event for sync process
                .flatMap(role -> {
                    Event event = new Event(Type.ROLE, new Payload(role.getId(), role.getReferenceType(), role.getReferenceId(), Action.CREATE));
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
    public Single<Role> create(String domain, NewRole newRole, User principal) {

        return create(ReferenceType.DOMAIN, domain, newRole, principal);
    }

    @Override
    public Single<Role> update(ReferenceType referenceType, String referenceId, String id, UpdateRole updateRole, User principal) {
        LOGGER.debug("Update a role {} for {} {}", id, referenceType, referenceId);

        return findById(referenceType, referenceId, id)
                .map(role -> {
                    if (role.isSystem()) {
                        throw new SystemRoleUpdateException(id);
                    }
                    return role;
                })
                .flatMap(oldRole -> {
                    // check if role name is unique
                    return checkRoleUniqueness(updateRole.getName(), oldRole.getId(), referenceType, referenceId)
                            .flatMap(irrelevant -> {
                                Role roleToUpdate = new Role(oldRole);
                                roleToUpdate.setName(updateRole.getName());
                                roleToUpdate.setDescription(updateRole.getDescription());
                                roleToUpdate.setPermissions(Permission.unflatten(updateRole.getPermissions()));
                                roleToUpdate.setOauthScopes(updateRole.getOauthScopes());
                                roleToUpdate.setUpdatedAt(new Date());
                                return roleRepository.update(roleToUpdate)
                                        // create event for sync process
                                        .flatMap(role -> {
                                            Event event = new Event(Type.ROLE, new Payload(role.getId(), role.getReferenceType(), role.getReferenceId(), Action.UPDATE));
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
    public Single<Role> update(String domain, String id, UpdateRole updateRole, User principal) {

        return update(ReferenceType.DOMAIN, domain, id, updateRole, principal);
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String roleId, User principal) {
        LOGGER.debug("Delete role {}", roleId);
        return roleRepository.findById(referenceType, referenceId, roleId)
                .switchIfEmpty(Maybe.error(new RoleNotFoundException(roleId)))
                .map(role -> {
                    if (role.isSystem()) {
                        throw new SystemRoleDeleteException(roleId);
                    }
                    return role;
                })
                .flatMapCompletable(role -> roleRepository.delete(roleId)
                        .andThen(Completable.fromSingle(eventService.create(new Event(Type.ROLE, new Payload(role.getId(), role.getReferenceType(), role.getReferenceId(), Action.DELETE)))))
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
    public Completable createOrUpdateSystemRoles() {

        List<Role> roles = buildAllSystemRoles();

        return Observable.fromIterable(roles)
                .flatMapCompletable(this::upsert);
    }

    private Completable upsert(Role role) {
        return roleRepository.findByNameAndAssignableType(role.getReferenceType(), role.getReferenceId(), role.getName(), role.getAssignableType())
                .map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(optRole -> {
                    if (!optRole.isPresent()) {
                        LOGGER.debug("Create a system role {}", role.getAssignableType() + ":" + role.getName());
                        role.setCreatedAt(new Date());
                        role.setUpdatedAt(role.getCreatedAt());
                        return roleRepository.create(role)
                                .flatMap(role1 -> {
                                    Event event = new Event(Type.ROLE, new Payload(role1.getId(), role1.getReferenceType(), role1.getReferenceId(), Action.CREATE));
                                    return eventService.create(event).flatMap(__ -> Single.just(role1));
                                })
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof AbstractManagementException) {
                                        return Single.error(ex);
                                    }
                                    LOGGER.error("An error occurs while trying to create a system role {}", role.getAssignableType() + ":" + role.getName(), ex);
                                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a role", ex));
                                })
                                .doOnSuccess(role1 -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).role(role1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_CREATED).throwable(throwable)))
                                .toCompletable();
                    } else {
                        // check if permission set has changed
                        Role currentRole = optRole.get();
                        if (permissionsAreEquals(currentRole, role)) {
                            return Completable.complete();
                        }
                        LOGGER.debug("Update a system role {}", role.getAssignableType() + ":" + role.getName());
                        // update the role
                        role.setId(currentRole.getId());
                        role.setPermissions(role.getPermissions());
                        role.setUpdatedAt(new Date());
                        return roleRepository.update(role)
                                .flatMap(role1 -> {
                                    Event event = new Event(Type.ROLE, new Payload(role1.getId(), role1.getReferenceType(), role1.getReferenceId(), Action.UPDATE));
                                    return eventService.create(event).flatMap(__ -> Single.just(role1));
                                })
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof AbstractManagementException) {
                                        return Single.error(ex);
                                    }
                                    LOGGER.error("An error occurs while trying to update a system role {}", role.getAssignableType() + ":" + role.getName(), ex);
                                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a role", ex));
                                })
                                .doOnSuccess(role1 -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_UPDATED).oldValue(currentRole).role(role1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(RoleAuditBuilder.class).type(EventType.ROLE_UPDATED).throwable(throwable)))
                                .toCompletable();
                    }
                });

    }

    private Single<Set<Role>> checkRoleUniqueness(String roleName, String roleId, ReferenceType referenceType, String referenceId) {
        return roleRepository.findAll(referenceType, referenceId)
                .collect(HashSet<Role>::new, Set::add)
                .flatMap(roles -> {
                    if (roles.stream()
                            .filter(role -> !role.getId().equals(roleId))
                            .anyMatch(role -> role.getName().equals(roleName))) {
                        throw new RoleAlreadyExistsException(roleName);
                    }
                    return Single.just(roles);
                });
    }

    private boolean permissionsAreEquals(Role role1, Role role2) {

        return Objects.equals(role1.getPermissions(), role2.getPermissions())
                && Objects.equals(role1.getOauthScopes(), role2.getOauthScopes());
    }

    private Flowable<Role> findAllSystem(ReferenceType assignableType) {

        LOGGER.debug("Find all global system roles");

        // Exclude roles internal only and non assignable roles.
        return roleRepository.findAll(ReferenceType.PLATFORM, Platform.DEFAULT)
                .filter(role -> role.isSystem() && !role.isInternalOnly())
                .filter(role -> assignableType == null || role.getAssignableType() == assignableType);
    }

    private static List<Role> buildAllSystemRoles() {

        List<Role> roles = new ArrayList<>();

        // Create PRIMARY_OWNER and ADMIN roles (we consider admin and primary owner have same permissions).
        Map<Permission, Set<Acl>> organizationAdminPermissions = Permission.allPermissionAcls(ReferenceType.ORGANIZATION);
        Map<Permission, Set<Acl>> platformAdminPermissions = Permission.allPermissionAcls(ReferenceType.PLATFORM);
        Map<Permission, Set<Acl>> domainAdminPermissions = Permission.allPermissionAcls(ReferenceType.DOMAIN);
        Map<Permission, Set<Acl>> applicationAdminPermissions = Permission.allPermissionAcls(ReferenceType.APPLICATION);

        organizationAdminPermissions.put(Permission.ORGANIZATION, Acl.of(READ));
        organizationAdminPermissions.put(Permission.ORGANIZATION_SETTINGS, Acl.of(READ, UPDATE));
        organizationAdminPermissions.put(Permission.ORGANIZATION_AUDIT, Acl.of(READ));

        domainAdminPermissions.put(Permission.DOMAIN_SETTINGS, Acl.of(READ, UPDATE));
        domainAdminPermissions.put(Permission.DOMAIN_AUDIT, Acl.of(READ));

        roles.add(buildSystemRole("PLATFORM_ADMIN", ReferenceType.PLATFORM, platformAdminPermissions));
        roles.add(buildSystemRole("ORGANIZATION_ADMIN", ReferenceType.ORGANIZATION, organizationAdminPermissions));
        roles.add(buildSystemRole("DOMAIN_ADMIN", ReferenceType.DOMAIN, domainAdminPermissions));
        roles.add(buildSystemRole("APPLICATION_ADMIN", ReferenceType.APPLICATION, applicationAdminPermissions));
        roles.add(buildSystemRole("ORGANIZATION_PRIMARY_OWNER", ReferenceType.ORGANIZATION, organizationAdminPermissions));
        roles.add(buildSystemRole("DOMAIN_PRIMARY_OWNER", ReferenceType.DOMAIN, domainAdminPermissions));
        roles.add(buildSystemRole("APPLICATION_PRIMARY_OWNER", ReferenceType.APPLICATION, applicationAdminPermissions));

        // Create USER roles.
        Map<Permission, Set<Acl>> organizationUserPermissions = new HashMap<>();
        Map<Permission, Set<Acl>> domainUserPermissions = new HashMap<>();
        Map<Permission, Set<Acl>> applicationUserPermissions = new HashMap<>();

        organizationUserPermissions.put(Permission.ORGANIZATION, Acl.of(READ));
        organizationUserPermissions.put(Permission.ORGANIZATION_GROUP, Acl.of(READ));
        organizationUserPermissions.put(Permission.ORGANIZATION_ROLE, Acl.of(READ));
        organizationUserPermissions.put(Permission.ORGANIZATION_TAG, Acl.of(READ));
        // Note : for now, there is only one 'DEFAULT' environment which is not known by AM users. Give read permission on all organization's environment in order to make things work.
        organizationUserPermissions.put(Permission.ENVIRONMENT, Acl.of(READ));

        domainUserPermissions.put(Permission.DOMAIN, Acl.of(READ));
        domainUserPermissions.put(Permission.DOMAIN_SCOPE, Acl.of(READ));
        domainUserPermissions.put(Permission.DOMAIN_EXTENSION_GRANT, Acl.of(READ));
        domainUserPermissions.put(Permission.DOMAIN_CERTIFICATE, Acl.of(READ));

        applicationUserPermissions.put(Permission.APPLICATION, Acl.of(READ));

        roles.add(buildSystemRole("ORGANIZATION_USER", ReferenceType.ORGANIZATION, organizationUserPermissions));
        roles.add(buildSystemRole("DOMAIN_USER", ReferenceType.DOMAIN, domainUserPermissions));
        roles.add(buildSystemRole("APPLICATION_USER", ReferenceType.APPLICATION, applicationUserPermissions));

        return roles;
    }

    private static Role buildSystemRole(String name, ReferenceType assignableType, Map<Permission, Set<Acl>> permissions) {

        Role role = new Role();
        role.setId(RandomString.generate());
        role.setName(name);
        role.setSystem(true);
        role.setAssignableType(assignableType);
        role.setReferenceType(ReferenceType.PLATFORM);
        role.setReferenceId(Platform.DEFAULT);
        role.setPermissions(permissions);

        return role;
    }
}
