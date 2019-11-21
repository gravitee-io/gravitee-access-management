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
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    protected boolean hasPermission(User authenticatedUser, RolePermission rolePermission, RolePermissionAction action) {
        if (authenticatedUser == null) {
            return false;
        }
        return roleManager.hasPermission(authenticatedUser.getRoles(), rolePermission, action);
    }

    protected <T extends Resource> T filterResource(T resource, ReferenceType referenceType, User authenticatedUser) {
        List<T> filteredResources = filterResources(Collections.singletonList(resource), referenceType, authenticatedUser);
        return (filteredResources != null && !filteredResources.isEmpty()) ? filteredResources.get(0) : null;
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
}
