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
package io.gravitee.am.management.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.ResourceForbiddenAccessException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractNotFoundException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.repository.utils.RepositoryConstants.DEFAULT_MAX_CONCURRENCY;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class PermissionService {

    private static final long PARENT_CACHE_MAX_SIZE = 10_000;
    private static final long PARENT_CACHE_EXPIRE_AFTER_WRITE_SECONDS = 60;

    private final MembershipService membershipService;
    private final OrganizationGroupService orgGroupService;
    private final RoleService roleService;
    private final EnvironmentService environmentService;
    private final DomainService domainService;
    private final ApplicationService applicationService;
    private final ProtectedResourceService protectedResourceService;
    private final Cache<Reference, Reference> parentCache;

    public PermissionService(MembershipService membershipService,
                             OrganizationGroupService organizationGroupService,
                             RoleService roleService,
                             EnvironmentService environmentService,
                             DomainService domainService,
                             ApplicationService applicationService,
                             ProtectedResourceService protectedResourceService) {
        this.membershipService = membershipService;
        this.orgGroupService = organizationGroupService;
        this.roleService = roleService;
        this.environmentService = environmentService;
        this.domainService = domainService;
        this.applicationService = applicationService;
        this.protectedResourceService = protectedResourceService;
        this.parentCache = CacheBuilder.newBuilder()
                .maximumSize(PARENT_CACHE_MAX_SIZE)
                .expireAfterWrite(PARENT_CACHE_EXPIRE_AFTER_WRITE_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    public Single<Map<Permission, Set<Acl>>> findAllPermissions(User user, ReferenceType referenceType, String referenceId) {

        return findMembershipPermissions(user, Collections.singletonMap(referenceType, referenceId).entrySet().stream())
                .map(this::aclsPerPermission);
    }

    public Flowable<String> getReferenceIdsWithPermission(User user, ReferenceType referenceType, Permission permission, Set<Acl> acls) {
        return findMembershipPermissions(user, referenceType)
                .flattenStreamAsFlowable(map -> map.entrySet().stream()
                        .filter(entry -> entry.getValue().getOrDefault(permission, Set.of()).containsAll(acls))
                        .map(entry -> entry.getKey().getReferenceId()));
    }

    public Single<Boolean> hasPermission(User user, PermissionAcls permissions) {

        return haveConsistentReferenceIds(permissions)
                .flatMap(consistent -> {
                    if (consistent) {
                        return findMembershipPermissions(user, permissions.referenceStream())
                                .map(permissions::match);
                    }
                    return Single.just(false);
                });
    }

    /**
     * Make sure the caller does not hand out more than what it holds: the permissions must be held by the caller on the
     * given scope (directly or through a parent). Platform administrators and primary owners of the organization may grant anything.
     *
     * @param flatPermissions the permissions to grant, as <code>&lt;permission&gt;_&lt;acl&gt;</code>
     */
    public Completable checkCanGrant(User caller, String organizationId, Reference scope, List<String> flatPermissions) {

        return Completable.defer(() -> {
            final Map<Permission, Set<Acl>> granted;
            try {
                granted = Permission.unflatten(flatPermissions);
            } catch (IllegalArgumentException e) {
                return Completable.error(new InvalidRoleException(e.getMessage()));
            }
            return checkCanGrant(caller, organizationId, scope, granted);
        });
    }

    /**
     * Same as {@link #checkCanGrant(User, String, Reference, List)} for the permissions carried by the given role.
     */
    public Completable checkCanGrantRole(User caller, String organizationId, Reference scope, String roleId) {

        return roleService.findById(roleId)
                .switchIfEmpty(Maybe.error(new RoleNotFoundException(roleId)))
                .flatMapCompletable(role -> checkCanGrant(caller, organizationId, scope, role.getPermissionAcls()));
    }

    private Completable checkCanGrant(User caller, String organizationId, Reference scope, Map<Permission, Set<Acl>> granted) {

        if (granted == null || granted.isEmpty()) {
            return Completable.complete();
        }
        return isPlatformAdminOrOwner(caller.getId(), organizationId)
                .flatMapCompletable(privileged -> {
                    if (privileged) {
                        return Completable.complete();
                    }
                    return findInheritedPermissions(caller, scope)
                            .flatMapCompletable(held -> {
                                final String missing = missingPermissions(granted, held);
                                return missing.isEmpty() ?
                                        Completable.complete() :
                                        Completable.error(new ResourceForbiddenAccessException("Unable to grant permissions you do not hold: " + missing));
                            });
                });
    }

    /**
     * The caller may act on another user of the organization only if this user does not hold more than the caller:
     * the platform administrators and the primary owners of the organization can only be handled by their peers.
     */
    public Completable checkCanManageUser(User caller, String organizationId, String targetUserId) {

        if (caller.getId() != null && caller.getId().equals(targetUserId)) {
            return Completable.complete();
        }
        return isPlatformAdminOrOwner(caller.getId(), organizationId)
                .flatMapCompletable(callerPrivileged -> {
                    if (callerPrivileged) {
                        return Completable.complete();
                    }
                    return isPlatformAdminOrOwner(targetUserId, organizationId)
                            .flatMapCompletable(targetPrivileged -> {
                                if (targetPrivileged) {
                                    return Completable.error(new ResourceForbiddenAccessException("Unable to manage a user with more privileges than yours"));
                                }
                                final DefaultUser target = new DefaultUser();
                                target.setId(targetUserId);
                                // the permissions of the default organization role are expected from every user, even when
                                // the caller got another role in its place
                                final Single<Map<Permission, Set<Acl>>> baseline = roleService.findDefaultRole(organizationId, DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION)
                                        .map(Role::getPermissionAcls)
                                        .defaultIfEmpty(Collections.emptyMap());
                                return Single.zip(
                                                findAllPermissions(caller, ReferenceType.ORGANIZATION, organizationId),
                                                findAllPermissions(target, ReferenceType.ORGANIZATION, organizationId),
                                                baseline,
                                                (held, targetHeld, defaults) -> {
                                                    final Map<Permission, Set<Acl>> callerHeld = new HashMap<>();
                                                    held.forEach((permission, acls) -> callerHeld.computeIfAbsent(permission, key -> new HashSet<>()).addAll(acls));
                                                    defaults.forEach((permission, acls) -> callerHeld.computeIfAbsent(permission, key -> new HashSet<>()).addAll(acls));
                                                    return missingPermissions(targetHeld, callerHeld);
                                                })
                                        .flatMapCompletable(missing -> missing.isEmpty() ?
                                                Completable.complete() :
                                                Completable.error(new ResourceForbiddenAccessException("Unable to manage a user with more privileges than yours")));
                            });
                });
    }

    /**
     * An account access token can be created for oneself, or by a platform administrator or a primary owner of the organization.
     */
    public Completable checkCanCreateAccountToken(User caller, String organizationId, String targetUserId) {

        if (caller.getId() != null && caller.getId().equals(targetUserId)) {
            return Completable.complete();
        }
        return isPlatformAdminOrOwner(caller.getId(), organizationId)
                .flatMapCompletable(privileged -> privileged ?
                        Completable.complete() :
                        Completable.error(new ResourceForbiddenAccessException("Account access tokens can only be created for yourself")));
    }

    private Single<Boolean> isPlatformAdminOrOwner(String userId, String organizationId) {

        if (userId == null) {
            return Single.error(new InvalidUserException("Specified user is invalid"));
        }
        final MembershipCriteria criteria = new MembershipCriteria();
        criteria.setUserId(userId);

        return Flowable.merge(
                        membershipService.findByCriteria(ReferenceType.PLATFORM, Platform.DEFAULT, criteria),
                        membershipService.findByCriteria(ReferenceType.ORGANIZATION, organizationId, criteria))
                .map(Membership::getRoleId)
                .distinct()
                .toList()
                .flatMap(roleIds -> roleIds.isEmpty() ?
                        Single.just(false) :
                        roleService.findByIdIn(roleIds)
                                .map(roles -> roles.stream().anyMatch(role -> role.isSystem()
                                        && (SystemRole.PLATFORM_ADMIN.name().equals(role.getName()) || SystemRole.ORGANIZATION_PRIMARY_OWNER.name().equals(role.getName())))));
    }

    /**
     * Permissions held by the user on the reference or inherited from one of its parents.
     */
    private Single<Map<Permission, Set<Acl>>> findInheritedPermissions(User user, Reference reference) {

        return referenceWithParents(reference)
                .flatMapSingle(ref -> findAllPermissions(user, ref.type(), ref.id()))
                .collect(HashMap::new, (Map<Permission, Set<Acl>> all, Map<Permission, Set<Acl>> permissions) ->
                        permissions.forEach((permission, acls) -> all.computeIfAbsent(permission, key -> new HashSet<>()).addAll(acls)));
    }

    private Flowable<Reference> referenceWithParents(Reference reference) {

        return Flowable.just(reference)
                .concatWith(parentOf(reference).flatMapPublisher(this::referenceWithParents));
    }

    private static String missingPermissions(Map<Permission, Set<Acl>> required, Map<Permission, Set<Acl>> held) {

        return required.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(acl -> !held.getOrDefault(entry.getKey(), Set.of()).contains(acl))
                        .map(acl -> entry.getKey().name().toLowerCase() + "_" + acl.name().toLowerCase()))
                .sorted()
                .collect(Collectors.joining(", "));
    }

    protected Single<Boolean> haveConsistentReferenceIds(PermissionAcls permissionAcls) {

        return Single.defer(() -> {
            Map<ReferenceType, String> referenceMap = permissionAcls.referenceStream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (referenceMap.size() == 1) {
                // There is only one type. Consistency is ok.
                return Single.just(true);
            }

            // When checking acls for multiple types in same time, we need to check if tuples [ReferenceType - ReferenceId] are consistent each other.
            // Ex: when we check for DOMAIN_READ permission on domain X or DOMAIN_READ environment Y, we need to make sure that domain X is effectively attached to domain Y and grand permission by inheritance.
            String applicationId = referenceMap.get(ReferenceType.APPLICATION);
            String protectedResourceId = referenceMap.get(ReferenceType.PROTECTED_RESOURCE);
            String domainId = referenceMap.get(ReferenceType.DOMAIN);
            String environmentId = referenceMap.get(ReferenceType.ENVIRONMENT);
            String organizationId = referenceMap.get(ReferenceType.ORGANIZATION);

            List<Single<Boolean>> checks = new ArrayList<>();

            if (environmentId != null) {
                checks.add(isEnvironmentConsistent(environmentId, organizationId));
            }

            if (protectedResourceId != null) {
                checks.add(isProtectedResourceConsistent(protectedResourceId, domainId, environmentId, organizationId));
            }

            if (domainId != null) {
                checks.add(isDomainConsistent(domainId, environmentId, organizationId));
            }

            if (applicationId != null) {
                checks.add(isApplicationConsistent(applicationId, domainId, environmentId, organizationId));
            }

            return Single.concat(checks)
                    .all(consistent -> consistent)
                    .doOnSuccess(consistent -> {
                        if (!consistent) {
                            log.debug("Inconsistent reference ids, permission denied: application={}, domain={}, environment={}, organization={}",
                                    applicationId, domainId, environmentId, organizationId);
                        }
                    });
        });
    }

    private Single<Boolean> isApplicationConsistent(String applicationId, String domainId, String environmentId, String organizationId) {

        if (domainId == null && environmentId == null && organizationId == null) {
            return Single.just(true);
        }

        return parentOf(Reference.application(applicationId))
                .flatMap(parent -> {
                    if (domainId != null) {
                        return Maybe.just(parent.matches(ReferenceType.DOMAIN, domainId));
                    }
                    if (parent.type() != ReferenceType.DOMAIN) {
                        return Maybe.just(false);
                    }
                    return isDomainConsistent(parent.id(), environmentId, organizationId).toMaybe();
                })
                .defaultIfEmpty(false);
    }

    private Single<Boolean> isProtectedResourceConsistent(String protectedResourceId, String domainId, String environmentId, String organizationId) {

        if (domainId == null && environmentId == null && organizationId == null) {
            return Single.just(true);
        }

        return parentOf(Reference.protectedResource(protectedResourceId))
                .flatMap(parent -> {
                    if (domainId != null) {
                        return Maybe.just(parent.matches(ReferenceType.DOMAIN, domainId));
                    }
                    if (parent.type() != ReferenceType.DOMAIN) {
                        return Maybe.just(false);
                    }
                    return isDomainConsistent(parent.id(), environmentId, organizationId).toMaybe();
                })
                .defaultIfEmpty(false);
    }

    private Single<Boolean> isDomainConsistent(String domainId, String environmentId, String organizationId) {

        if (environmentId == null && organizationId == null) {
            return Single.just(true);
        }

        return parentOf(Reference.domain(domainId))
                .flatMap(parent -> {
                    if (environmentId != null) {
                        return Maybe.just(parent.matches(ReferenceType.ENVIRONMENT, environmentId));
                    }
                    if (parent.type() != ReferenceType.ENVIRONMENT) {
                        return Maybe.just(false);
                    }
                    return isEnvironmentConsistent(parent.id(), organizationId).toMaybe();
                })
                .defaultIfEmpty(false);
    }

    private Single<Boolean> isEnvironmentConsistent(String environmentId, String organizationId) {

        if (organizationId == null) {
            return Single.just(true);
        }

        return parentOf(Reference.environment(environmentId))
                .map(parent -> parent.matches(ReferenceType.ORGANIZATION, organizationId))
                .defaultIfEmpty(false);
    }

    private Maybe<Reference> parentOf(Reference reference) {

        return Maybe.defer(() -> {
            Reference cached = parentCache.getIfPresent(reference);

            if (cached != null) {
                return Maybe.just(cached);
            }

            return resolveParent(reference).doOnSuccess(parent -> parentCache.put(reference, parent));
        });
    }

    private Maybe<Reference> resolveParent(Reference reference) {

        return switch (reference.type()) {
            case APPLICATION -> applicationService.findById(reference.id())
                    .flatMap(application -> parentReference(ReferenceType.DOMAIN, application.getDomain()));
            case PROTECTED_RESOURCE -> protectedResourceService.findById(reference.id())
                    .flatMap(resource -> parentReference(ReferenceType.DOMAIN, resource.getDomainId()));
            case DOMAIN -> domainService.findById(reference.id())
                    .flatMap(domain -> parentReference(domain.getReferenceType(), domain.getReferenceId()));
            case ENVIRONMENT -> environmentService.findById(reference.id())
                    .toMaybe()
                    .flatMap(environment -> parentReference(ReferenceType.ORGANIZATION, environment.getOrganizationId()))
                    .onErrorResumeNext(PermissionService::emptyIfNotFound);
            default -> Maybe.empty();
        };
    }

    private static Maybe<Reference> parentReference(ReferenceType type, String id) {

        return type == null || id == null ? Maybe.empty() : Maybe.just(new Reference(type, id));
    }

    private static Maybe<Reference> emptyIfNotFound(Throwable throwable) {

        if (throwable instanceof AbstractNotFoundException) {
            return Maybe.empty();
        }

        return Maybe.error(throwable);
    }

    private Single<Map<Membership, Map<Permission, Set<Acl>>>> findMembershipPermissions(User user, Stream<Map.Entry<ReferenceType, String>> referenceStream) {

        if (user.getId() == null) {
            return Single.error(new InvalidUserException("Specified user is invalid"));
        }

        return orgGroupService.findByMember(user.getId())
                .map(Group::getId)
                .collect(Collectors.toList())
                .flatMap(userGroupIds -> {
                    MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setUserId(user.getId());
                    criteria.setGroupIds(userGroupIds.isEmpty() ? null : userGroupIds);
                    criteria.setLogicalOR(true);

                    // Get all user and group memberships.
                    return Flowable.merge(referenceStream.map(p -> membershipService.findByCriteria(p.getKey(), p.getValue(), criteria)).collect(Collectors.toList()), DEFAULT_MAX_CONCURRENCY)
                            .toList()
                            .flatMap(allMemberships -> {

                                if (allMemberships.isEmpty()) {
                                    return Single.just(Collections.emptyMap());
                                }

                                // Get all roles.
                                return roleService.findByIdIn(allMemberships.stream().map(Membership::getRoleId).distinct().collect(Collectors.toList()))
                                        .map(allRoles -> permissionsPerMembership(allMemberships, allRoles));
                            });
                });
    }

    private Single<Map<Membership, Map<Permission, Set<Acl>>>> findMembershipPermissions(User user, ReferenceType referenceType) {

        if (user.getId() == null) {
            return Single.error(new InvalidUserException("Specified user is invalid"));
        }

        return orgGroupService.findByMember(user.getId())
                .map(Group::getId)
                .collect(Collectors.toList())
                .flatMap(userGroupIds -> {
                    MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setUserId(user.getId());
                    criteria.setGroupIds(userGroupIds.isEmpty() ? null : userGroupIds);
                    criteria.setLogicalOR(true);

                    // Get all user and group memberships.
                    return membershipService.findByCriteria(referenceType, criteria)
                            .toList()
                            .flatMap(allMemberships -> {

                                if (allMemberships.isEmpty()) {
                                    return Single.just(Collections.emptyMap());
                                }

                                // Get all roles.
                                return roleService.findByIdIn(allMemberships.stream().map(Membership::getRoleId).distinct().collect(Collectors.toList()))
                                        .map(allRoles -> permissionsPerMembership(allMemberships, allRoles));
                            });
                });
    }

    private Map<Membership, Map<Permission, Set<Acl>>> permissionsPerMembership(List<Membership> allMemberships, Set<Role> allRoles) {

        Map<String, Role> allRolesById = allRoles.stream().collect(Collectors.toMap(Role::getId, role -> role));
        Map<Membership, Map<Permission, Set<Acl>>> rolesPerMembership = allMemberships.stream().collect(Collectors.toMap(membership -> membership, o -> new HashMap<>()));

        rolesPerMembership.forEach((membership, permissions) -> {
            Role role = allRolesById.get(membership.getRoleId());

            // Need to check the membership role is well assigned (ie: the role is assignable with the membership type).
            if (role != null && role.getAssignableType() == membership.getReferenceType()) {
                Map<Permission, Set<Acl>> rolePermissions = role.getPermissionAcls();

                // Compute membership permission acls.
                rolePermissions.forEach((permission, acls) -> permissions.merge(permission, acls, (acls1, acls2) -> {
                    acls1.addAll(acls2);
                    return acls1;
                }));
            }
        });

        return rolesPerMembership;
    }

    private Map<Permission, Set<Acl>> aclsPerPermission(Map<Membership, Map<Permission, Set<Acl>>> rolesPerMembership) {

        Map<Permission, Set<Acl>> permissions = new HashMap<>();

        rolesPerMembership.forEach((membership, membershipPermissions) ->
                membershipPermissions.forEach((permission, acls) -> {

                    // Compute acls of same Permission.
                    if (permissions.containsKey(permission)) {
                        permissions.get(permission).addAll(acls);
                    } else {
                        permissions.put(permission, new HashSet<>(acls));
                    }
                }));

        return permissions;
    }
}
