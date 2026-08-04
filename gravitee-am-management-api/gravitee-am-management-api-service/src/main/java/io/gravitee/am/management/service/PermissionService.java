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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.permissions.PermissionAcls;
<<<<<<< HEAD
import io.gravitee.am.model.*;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.*;
=======
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractNotFoundException;
>>>>>>> 69920fc17 (fix: permissions key cache)
import io.gravitee.am.service.exception.InvalidUserException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class PermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionService.class);

    private static final long PARENT_CACHE_MAX_SIZE = 10_000;
    private static final long PARENT_CACHE_EXPIRE_AFTER_WRITE_SECONDS = 60;

    private final MembershipService membershipService;
    private final OrganizationGroupService orgGroupService;
    private final RoleService roleService;
    private final EnvironmentService environmentService;
    private final DomainService domainService;
    private final ApplicationService applicationService;
<<<<<<< HEAD
    private final ProtectedResourceService protectedResourceService;
    private final Map<String, Boolean> consistencyCache;
=======
    private final Cache<Reference, Reference> parentCache;
>>>>>>> 69920fc17 (fix: permissions key cache)

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
<<<<<<< HEAD
        this.protectedResourceService = protectedResourceService;
        this.consistencyCache = new ConcurrentHashMap<>();
=======
        this.parentCache = CacheBuilder.newBuilder()
                .maximumSize(PARENT_CACHE_MAX_SIZE)
                .expireAfterWrite(PARENT_CACHE_EXPIRE_AFTER_WRITE_SECONDS, TimeUnit.SECONDS)
                .build();
>>>>>>> 69920fc17 (fix: permissions key cache)
    }

    public Single<Map<Permission, Set<Acl>>> findAllPermissions(User user, ReferenceType referenceType, String referenceId) {

        return findMembershipPermissions(user, Collections.singletonMap(referenceType, referenceId).entrySet().stream())
                .map(this::aclsPerPermission);
    }

    public Flowable<String> getReferenceIdsWithPermission(User user, ReferenceType referenceType, Permission permission, Set<Acl> acls) {
        return findMembershipPermissions(user, referenceType)
                .flattenStreamAsFlowable(map -> map.entrySet().stream()
                        .filter(entry -> entry.getValue().get(permission).containsAll(acls))
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

<<<<<<< HEAD
            String key = StringUtils.arrayToDelimitedString(new String[]{applicationId, protectedResourceId, domainId, environmentId, organizationId}, "#");
=======
            List<Single<Boolean>> checks = new ArrayList<>();
>>>>>>> 69920fc17 (fix: permissions key cache)

            if (environmentId != null) {
                checks.add(isEnvironmentConsistent(environmentId, organizationId));
            }

            if (protectedResourceId != null) {
                obs.add(isProtectedResourceIdConsistent(protectedResourceId, domainId, environmentId, organizationId));
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
                            LOGGER.debug("Inconsistent reference ids, permission denied: application={}, domain={}, environment={}, organization={}",
                                    applicationId, domainId, environmentId, organizationId);
                        }
                    });
        });
    }

    private Single<Boolean> isApplicationConsistent(String applicationId, String domainId, String environmentId, String organizationId) {

        if (domainId == null && environmentId == null && organizationId == null) {
            return Single.just(true);
        }

<<<<<<< HEAD
        return applicationService.findById(applicationId)
                .map(Application::getDomain)
                .flatMapSingle(storedDomainId -> {
                    if (domainId != null) {
                        return Single.just(storedDomainId.equals(domainId));
                    } else {
                        // Need to fetch the domain to check if it belongs to the environment / organization.
                        return isDomainIdConsistent(storedDomainId, environmentId, organizationId);
                    }
                }).toSingle();
    }

    private Single<Boolean> isProtectedResourceIdConsistent(String protectedResourceId, String domainId, String environmentId, String organizationId) {

        if(domainId == null && environmentId == null && organizationId == null) {
            return Single.just(true);
        }

        return protectedResourceService.findById(protectedResourceId)
                .map(ProtectedResource::getDomainId)
                .flatMapSingle(storedDomainId -> {
                    if (domainId != null) {
                        return Single.just(storedDomainId.equals(domainId));
                    } else {
                        // Need to fetch the domain to check if it belongs to the environment / organization.
                        return isDomainIdConsistent(storedDomainId, environmentId, organizationId);
=======
        return parentOf(Reference.application(applicationId))
                .flatMap(parent -> {
                    if (domainId != null) {
                        return Maybe.just(parent.matches(ReferenceType.DOMAIN, domainId));
>>>>>>> 69920fc17 (fix: permissions key cache)
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
