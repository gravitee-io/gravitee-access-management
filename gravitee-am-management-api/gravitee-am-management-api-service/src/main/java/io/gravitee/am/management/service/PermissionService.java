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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.*;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PermissionService {

    private final MembershipService membershipService;
    private final GroupService groupService;
    private final RoleService roleService;

    public PermissionService(MembershipService membershipService,
                             GroupService groupService,
                             RoleService roleService) {
        this.membershipService = membershipService;
        this.groupService = groupService;
        this.roleService = roleService;
    }

    public Single<Map<Permission, Set<Acl>>> findAllPermissions(User user, ReferenceType referenceType, String referenceId) {

        return findMembershipPermissions(user, Collections.singletonMap(referenceType, referenceId).entrySet().stream())
                .map(this::aclsPerPermission);
    }

    public Single<Boolean> hasPermission(User user, PermissionAcls permissions) {

        return findMembershipPermissions(user, permissions.referenceStream())
                .map(permissions::match);
    }

    private Single<Map<Membership, Map<Permission, Set<Acl>>>> findMembershipPermissions(User user, Stream<Map.Entry<ReferenceType, String>> referenceStream) {

        return groupService.findByMember(user.getId())
                .flattenAsFlowable(groups -> groups)
                .map(Group::getId)
                .toList()
                .flatMap(userGroupIds -> {
                    MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setUserId(user.getId());
                    criteria.setGroupIds(userGroupIds.isEmpty() ? null : userGroupIds);
                    criteria.setLogicalOR(true);

                    // Get all user and group memberships.
                    return Flowable.merge(referenceStream.map(p -> membershipService.findByCriteria(p.getKey(), p.getValue(), criteria)).collect(Collectors.toList()))
                            .toList()
                            .flatMap(allMemberships -> {

                                if (allMemberships.isEmpty()) {
                                    return Single.just(Collections.emptyMap());
                                }

                                // Get all roles.
                                return roleService.findByIdIn(allMemberships.stream().map(Membership::getRoleId).collect(Collectors.toList()))
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
                Map<Permission, Set<Acl>> rolePermissions = role.getPermissions();

                // Compute membership permission acls.
                rolePermissions.forEach((permission, acls) -> {
                    permissions.merge(permission, acls, (acls1, acls2) -> {
                        acls1.addAll(acls2);
                        return acls1;
                    });
                });
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