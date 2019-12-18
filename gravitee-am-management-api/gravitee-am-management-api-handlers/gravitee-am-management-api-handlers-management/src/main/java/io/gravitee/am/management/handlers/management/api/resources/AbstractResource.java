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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.manager.group.GroupManager;
import io.gravitee.am.management.handlers.management.api.manager.membership.MembershipManager;
import io.gravitee.am.management.handlers.management.api.manager.role.RoleManager;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Resource;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractResource {

    @Context
    protected SecurityContext securityContext;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private GroupManager groupManager;

    @Autowired
    private MembershipManager membershipManager;

    protected User getAuthenticatedUser() {
        if (isAuthenticated()) {
            return (User) ((UsernamePasswordAuthenticationToken) securityContext.getUserPrincipal()).getPrincipal();
        }
        return null;
    }

    protected boolean isAuthenticated() {
        return securityContext.getUserPrincipal() != null;
    }

    protected boolean isAdmin(User authenticatedUser) {
        if (authenticatedUser == null) {
            return false;
        }
        return roleManager.isAdminRoleGranted(authenticatedUser.getRoles());
    }

    protected boolean hasPermission(List<String> permissions, RolePermission rolePermission, RolePermissionAction action) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains(rolePermission.getPermission().getMask() + "_" + action.getMask());
    }

    protected <T extends Resource> List<T> filterResources(Collection<? extends T> resources, ReferenceType referenceType, User authenticatedUser) {
        // if user is admin, return all resources
        if (isAdmin(authenticatedUser)) {
            return new ArrayList<>(resources);
        }

        // check if authenticated user is a member of any of the resource list
        List<String> groups = groupManager.findByMember(authenticatedUser.getId()).stream().map(Group::getId).collect(Collectors.toList());
        return resources.stream().filter(resource -> {
            List<Membership> memberships = membershipManager.findByReference(resource.getId(), referenceType);
            if (memberships == null || memberships.isEmpty()) {
                return false;
            }
            List<String> membershipIds = memberships.stream().map(Membership::getMemberId).collect(Collectors.toList());
            return membershipIds.contains(authenticatedUser.getId()) || membershipIds.stream().anyMatch(mId -> groups.contains(mId));
        }).collect(Collectors.toList());
    }

    protected List<String> resourcePermissions(Resource resource, ReferenceType referenceType, User authenticatedUser) throws ForbiddenException {
        // if resource has no member, throw forbidden exception
        List<Membership> memberships = membershipManager.findByReference(resource.getId(), referenceType);
        if (memberships ==  null || memberships.isEmpty()) {
            throw new ForbiddenException();
        }

        // get user groups
        List<Group> groups = groupManager.findByMember(authenticatedUser.getId());
        List<String> groupIds = (groups != null) ? groups.stream().map(Group::getId).collect(Collectors.toList()) : Collections.emptyList();
        List<Membership> resourceMemberships = memberships.stream().filter(membership -> membership.getMemberId().equals(authenticatedUser.getId()) || groupIds.contains(membership.getMemberId())).collect(Collectors.toList());

        // if user or group is not a member of the resource, throw forbidden exception
        if (resourceMemberships == null || resourceMemberships.isEmpty()) {
            throw new ForbiddenException();
        }

        // check if the member has the resource permission
        List<String> roleIds = resourceMemberships.stream().map(Membership::getRole).collect(Collectors.toList());
        Set<Role> roles = roleManager.findByIdIn(roleIds);
        if (roles == null || roles.isEmpty()) {
            throw new ForbiddenException();
        }
        return roles.stream()
                .filter(role -> role.getPermissions() != null)
                .map(Role::getPermissions)
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

}
