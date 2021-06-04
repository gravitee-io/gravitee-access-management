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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.RoleAuditBuilder;
import io.reactivex.Observable;
import io.reactivex.*;
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
    public Single<Page<Role>> findByDomain(String domain, int page, int size) {
        return roleRepository.findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Single<Page<Role>> searchByDomain(String domain, String query, int page, int size) {
        return roleRepository.search(ReferenceType.DOMAIN, domain, query, page, size);
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
        LOGGER.debug("Find system role : {} for the type : {}", systemRole.name(), assignableType);
        return roleRepository.findByNameAndAssignableType(ReferenceType.PLATFORM, Platform.DEFAULT, systemRole.name(), assignableType)
                .filter(Role::isSystem)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find system role : {} for type : {}", systemRole.name(), assignableType, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find system role : %s for type : %s", systemRole.name(), assignableType), ex));
                });
    }

    @Override
    public Flowable<Role> findRolesByName(ReferenceType referenceType, String referenceId, ReferenceType assignableType, List<String> roleNames) {
        return roleRepository.findByNamesAndAssignableType(referenceType, referenceId, roleNames, assignableType)
                .onErrorResumeNext(ex -> {
                    String joinedRoles = roleNames.stream().collect(Collectors.joining(", "));
                    LOGGER.error("An error occurs while trying to find roles : {}", joinedRoles, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find roles : %s", joinedRoles), ex));
                });
    }

    @Override
    public Maybe<Role> findDefaultRole(String organizationId, DefaultRole defaultRole, ReferenceType assignableType) {
        LOGGER.debug("Find default role {} of organization {} for the type {}", defaultRole.name(), organizationId, assignableType);
        return roleRepository.findByNameAndAssignableType(ReferenceType.ORGANIZATION, organizationId, defaultRole.name(), assignableType)
                .filter(Role::isDefaultRole)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find default role {} of organization {} for the type {}", defaultRole.name(), organizationId, assignableType, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find default role %s of organization %s for type %s", defaultRole.name(), organizationId, assignableType), ex));
                });
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> ids) {
        LOGGER.debug("Find roles by ids: {}", ids);
        return roleRepository.findByIdIn(ids).collect(() -> (Set<Role>)new HashSet<Role>(), Set::add)
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
                    role.setPermissionAcls(new HashMap<>());
                    role.setOauthScopes(new ArrayList<>());
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
                .flatMap(role -> {
                    if (role.isSystem()) {
                        return Single.error(new SystemRoleUpdateException(role.getName()));
                    }

                    if(role.isDefaultRole() && !role.getName().equals(updateRole.getName())) {
                        return Single.error(new DefaultRoleUpdateException(role.getName()));
                    }

                    return Single.just(role);
                })
                .flatMap(oldRole -> {
                    // check if role name is unique
                    return checkRoleUniqueness(updateRole.getName(), oldRole.getId(), referenceType, referenceId)
                            .flatMap(irrelevant -> {
                                Role roleToUpdate = new Role(oldRole);
                                roleToUpdate.setName(updateRole.getName());
                                roleToUpdate.setDescription(updateRole.getDescription());
                                roleToUpdate.setPermissionAcls(Permission.unflatten(updateRole.getPermissions()));
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

        List<Role> roles = buildSystemRoles();

        return Observable.fromIterable(roles)
                .flatMapCompletable(this::upsert);
    }

    @Override
    public Completable createDefaultRoles(String organizationId) {

        List<Role> roles = buildDefaultRoles(organizationId);

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
                        role.setPermissionAcls(role.getPermissionAcls());
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

        return Objects.equals(role1.getPermissionAcls(), role2.getPermissionAcls())
                && Objects.equals(role1.getOauthScopes(), role2.getOauthScopes());
    }

    private Flowable<Role> findAllSystem(ReferenceType assignableType) {
        LOGGER.debug("Find all global system roles");

        // Exclude roles internal only and non assignable roles.
        return roleRepository.findAll(ReferenceType.PLATFORM, Platform.DEFAULT)
                .filter(role -> role.isSystem() && !role.isInternalOnly())
                .filter(role -> assignableType == null || role.getAssignableType() == assignableType);
    }

    private static List<Role> buildSystemRoles() {

        List<Role> roles = new ArrayList<>();

        // Create PRIMARY_OWNER roles and PLATFORM_ADMIN role.
        Map<Permission, Set<Acl>> platformAdminPermissions = Permission.allPermissionAcls(ReferenceType.PLATFORM);
        Map<Permission, Set<Acl>> organizationPrimaryOwnerPermissions = Permission.allPermissionAcls(ReferenceType.ORGANIZATION);
        Map<Permission, Set<Acl>> environmentPrimaryOwnerPermissions = Permission.allPermissionAcls(ReferenceType.ENVIRONMENT);
        Map<Permission, Set<Acl>> domainPrimaryOwnerPermissions = Permission.allPermissionAcls(ReferenceType.DOMAIN);
        Map<Permission, Set<Acl>> applicationPrimaryOwnerPermissions = Permission.allPermissionAcls(ReferenceType.APPLICATION);

        organizationPrimaryOwnerPermissions.put(Permission.ORGANIZATION, Acl.of(READ));
        organizationPrimaryOwnerPermissions.put(Permission.ORGANIZATION_SETTINGS, Acl.of(READ, UPDATE));
        organizationPrimaryOwnerPermissions.put(Permission.ORGANIZATION_AUDIT, Acl.of(READ, LIST));
        organizationPrimaryOwnerPermissions.put(Permission.ENVIRONMENT, Acl.of(READ, LIST));

        environmentPrimaryOwnerPermissions.put(Permission.ENVIRONMENT, Acl.of(READ));

        domainPrimaryOwnerPermissions.put(Permission.DOMAIN, Acl.of(READ, UPDATE, DELETE));
        domainPrimaryOwnerPermissions.put(Permission.DOMAIN_SETTINGS, Acl.of(READ, UPDATE));
        domainPrimaryOwnerPermissions.put(Permission.DOMAIN_AUDIT, Acl.of(READ, LIST));

        applicationPrimaryOwnerPermissions.put(Permission.APPLICATION, Acl.of(READ, UPDATE, DELETE));

        roles.add(buildSystemRole(SystemRole.PLATFORM_ADMIN.name(), ReferenceType.PLATFORM, platformAdminPermissions));
        roles.add(buildSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER.name(), ReferenceType.ORGANIZATION, organizationPrimaryOwnerPermissions));
        roles.add(buildSystemRole(SystemRole.ENVIRONMENT_PRIMARY_OWNER.name(), ReferenceType.ENVIRONMENT, environmentPrimaryOwnerPermissions));
        roles.add(buildSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER.name(), ReferenceType.DOMAIN, domainPrimaryOwnerPermissions));
        roles.add(buildSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER.name(), ReferenceType.APPLICATION, applicationPrimaryOwnerPermissions));

        return roles;
    }

    private List<Role> buildDefaultRoles(String organizationId) {

        List<Role> roles = new ArrayList<>();

        // Create OWNER and USER roles.
        Map<Permission, Set<Acl>> organizationOwnerPermissions = Permission.allPermissionAcls(ReferenceType.ORGANIZATION);
        Map<Permission, Set<Acl>> environmentOwnerPermissions = Permission.allPermissionAcls(ReferenceType.ENVIRONMENT);
        Map<Permission, Set<Acl>> domainOwnerPermissions = Permission.allPermissionAcls(ReferenceType.DOMAIN);
        Map<Permission, Set<Acl>> applicationOwnerPermissions = Permission.allPermissionAcls(ReferenceType.APPLICATION);

        organizationOwnerPermissions.put(Permission.ORGANIZATION, Acl.of(READ));
        organizationOwnerPermissions.put(Permission.ORGANIZATION_SETTINGS, Acl.of(READ, UPDATE));
        organizationOwnerPermissions.put(Permission.ORGANIZATION_AUDIT, Acl.of(READ, LIST));
        organizationOwnerPermissions.put(Permission.ENVIRONMENT, Acl.of(READ, LIST));

        environmentOwnerPermissions.put(Permission.ENVIRONMENT, Acl.of(READ));

        domainOwnerPermissions.put(Permission.DOMAIN, Acl.of(READ, UPDATE));
        domainOwnerPermissions.put(Permission.DOMAIN_SETTINGS, Acl.of(READ, UPDATE));
        domainOwnerPermissions.put(Permission.DOMAIN_AUDIT, Acl.of(READ, LIST));

        applicationOwnerPermissions.put(Permission.APPLICATION, Acl.of(READ, UPDATE));

        roles.add(buildDefaultRole(DefaultRole.ORGANIZATION_OWNER.name(), ReferenceType.ORGANIZATION, organizationId, organizationOwnerPermissions));
        roles.add(buildDefaultRole(DefaultRole.ENVIRONMENT_OWNER.name(), ReferenceType.ENVIRONMENT, organizationId, environmentOwnerPermissions));
        roles.add(buildDefaultRole(DefaultRole.DOMAIN_OWNER.name(), ReferenceType.DOMAIN, organizationId, domainOwnerPermissions));
        roles.add(buildDefaultRole(DefaultRole.APPLICATION_OWNER.name(), ReferenceType.APPLICATION, organizationId, applicationOwnerPermissions));

        // Create USER roles.
        Map<Permission, Set<Acl>> organizationUserPermissions = new HashMap<>();
        Map<Permission, Set<Acl>> environmentUserPermissions = new HashMap<>();
        Map<Permission, Set<Acl>> domainUserPermissions = new HashMap<>();
        Map<Permission, Set<Acl>> applicationUserPermissions = new HashMap<>();

        organizationUserPermissions.put(Permission.ORGANIZATION, Acl.of(READ));
        organizationUserPermissions.put(Permission.ORGANIZATION_GROUP, Acl.of(LIST));
        organizationUserPermissions.put(Permission.ORGANIZATION_ROLE, Acl.of(LIST));
        organizationUserPermissions.put(Permission.ORGANIZATION_TAG, Acl.of(LIST));
        organizationUserPermissions.put(Permission.ENVIRONMENT, Acl.of(LIST));

        environmentUserPermissions.put(Permission.ENVIRONMENT, Acl.of(READ));
        environmentUserPermissions.put(Permission.DOMAIN, Acl.of(LIST));

        domainUserPermissions.put(Permission.DOMAIN, Acl.of(READ));
        domainUserPermissions.put(Permission.DOMAIN_SCOPE, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_EXTENSION_GRANT, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_CERTIFICATE, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_IDENTITY_PROVIDER, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_FACTOR, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_RESOURCE, Acl.of(LIST));
        domainUserPermissions.put(Permission.APPLICATION, Acl.of(LIST));
        domainUserPermissions.put(Permission.DOMAIN_BOT_DETECTION, Acl.of(LIST));

        applicationUserPermissions.put(Permission.APPLICATION, Acl.of(READ));

        roles.add(buildDefaultRole(DefaultRole.ORGANIZATION_USER.name(), ReferenceType.ORGANIZATION, organizationId, organizationUserPermissions));
        roles.add(buildDefaultRole(DefaultRole.ENVIRONMENT_USER.name(), ReferenceType.ENVIRONMENT, organizationId, environmentUserPermissions));
        roles.add(buildDefaultRole(DefaultRole.DOMAIN_USER.name(), ReferenceType.DOMAIN, organizationId, domainUserPermissions));
        roles.add(buildDefaultRole(DefaultRole.APPLICATION_USER.name(), ReferenceType.APPLICATION, organizationId, applicationUserPermissions));

        return roles;
    }

    private static Role buildSystemRole(String name, ReferenceType assignableType, Map<Permission, Set<Acl>> permissions) {

        Role systemRole = buildRole(name, assignableType, ReferenceType.PLATFORM, Platform.DEFAULT, permissions);
        systemRole.setSystem(true);

        return systemRole;
    }

    private static Role buildDefaultRole(String name, ReferenceType assignableType, String organizationId, Map<Permission, Set<Acl>> permissions) {

        Role defaultRole = buildRole(name, assignableType, ReferenceType.ORGANIZATION, organizationId, permissions);
        defaultRole.setDefaultRole(true);

        return defaultRole;
    }

    private static Role buildRole(String name, ReferenceType assignableType, ReferenceType referenceType, String referenceId, Map<Permission, Set<Acl>> permissions) {

        Role role = new Role();
        role.setId(RandomString.generate());
        role.setName(name);
        role.setAssignableType(assignableType);
        role.setReferenceType(referenceType);
        role.setReferenceId(referenceId);
        role.setPermissionAcls(permissions);

        return role;
    }
}
