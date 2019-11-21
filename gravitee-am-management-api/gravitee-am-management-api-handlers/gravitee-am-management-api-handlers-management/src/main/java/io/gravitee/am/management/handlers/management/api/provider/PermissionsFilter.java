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
package io.gravitee.am.management.handlers.management.api.provider;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.manager.group.GroupManager;
import io.gravitee.am.management.handlers.management.api.manager.membership.MembershipManager;
import io.gravitee.am.management.handlers.management.api.manager.role.RoleManager;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PermissionsFilter implements ContainerRequestFilter {

    protected final Logger logger = LoggerFactory.getLogger(PermissionsFilter.class);

    @Context
    protected ResourceInfo resourceInfo;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private MembershipManager membershipManager;

    @Inject
    private GroupManager groupManager;

    @Inject
    private RoleManager roleManager;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (isAdmin()) {
            logger.debug("User [{}] has full access because of its ADMIN role", securityContext.getUserPrincipal().getName());
            return;
        }
        filter(getRequiredPermission(), requestContext);
    }

    protected void filter(Permissions permissions, ContainerRequestContext requestContext) {
        // no permission required, continue
        if (permissions == null || permissions.value().length == 0) {
            return;
        }

        // check permissions
        for (Permission permission : permissions.value()) {
            switch (permission.value().getScope()) {
                case MANAGEMENT:
                    checkPermission(permission);
                    break;
                case DOMAIN:
                    final String domainId = getId("domain", requestContext);
                    filterResource(domainId, ReferenceType.DOMAIN, permission);
                    break;
                case APPLICATION:
                    final String appId = getId("application", requestContext);
                    filterResource(appId, ReferenceType.APPLICATION, permission);
                    break;
                default:
                    throw new ForbiddenException();
            }
        }
    }

    private void checkPermission(Permission permission) {
        final User authenticatedUser = getAuthenticatedUser();
        List<String> roles = authenticatedUser.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new ForbiddenException();
        }
        if (!hasPermission(roles, permission)) {
            throw new ForbiddenException();
        }
    }

    private void filterResource(String referenceId, ReferenceType referenceType, Permission permission) {
        // if resource has no member, throw forbidden exception
        List<Membership> memberships = membershipManager.findByReference(referenceId, referenceType);
        if (memberships ==  null || memberships.isEmpty()) {
            throw new ForbiddenException();
        }

        // get user groups
        List<Group> groups = groupManager.findByMember(getAuthenticatedUser().getId());
        List<String> groupIds = (groups != null) ? groups.stream().map(Group::getId).collect(Collectors.toList()) : Collections.emptyList();
        List<Membership> resourceMemberships = memberships.stream().filter(membership -> membership.getMemberId().equals(getAuthenticatedUser().getId()) || groupIds.contains(membership.getMemberId())).collect(Collectors.toList());

        // if user or group is not a member of the resource, throw forbidden exception
        if (resourceMemberships == null || resourceMemberships.isEmpty()) {
            throw new ForbiddenException();
        }

        // check if the member has the resource permission
        List<String> roles = resourceMemberships.stream().map(Membership::getRole).collect(Collectors.toList());
        if (!hasPermission(roles, permission)) {
            throw new ForbiddenException();
        }
    }

    private boolean hasPermission(List<String> roleIds, Permission permission) {
        Set<Role> roles = roleManager.findByIdIn(roleIds);
        if (roles == null || roles.isEmpty()) {
            return false;
        }

        RolePermission rolePermission= permission.value();
        RolePermissionAction[] acls = permission.acls();

        return roles.stream().anyMatch(r -> rolePermission.getScope().getId() == r.getScope() &&
                r.getPermissions().containsAll(Arrays.asList(acls).stream().map(acl -> rolePermission.getPermission().getMask() + "_" + acl.getMask()).collect(Collectors.toList())));

    }

    private Permissions getRequiredPermission() {
        Permissions permission = resourceInfo.getResourceMethod().getDeclaredAnnotation(Permissions.class);
        if (permission == null) {
            return resourceInfo.getResourceClass().getDeclaredAnnotation(Permissions.class);
        }
        return permission;
    }

    private String getId(String key, ContainerRequestContext requestContext) {
        List<String> pathParams = requestContext.getUriInfo().getPathParameters().get(key);
        if (pathParams != null) {
            return pathParams.iterator().next();
        } else {
            List<String> queryParams = requestContext.getUriInfo().getQueryParameters().get(key);
            if (queryParams != null) {
                return queryParams.iterator().next();
            }
        }
        return null;
    }


    private boolean isAdmin() {
        if (!isAuthenticated()) {
            return false;
        }
        User user = getAuthenticatedUser();
        return roleManager.isAdminRoleGranted(user.getRoles());
    }

    private User getAuthenticatedUser() {
        if (isAuthenticated()) {
            return (User) ((UsernamePasswordAuthenticationToken) securityContext.getUserPrincipal()).getPrincipal();
        }
        return null;
    }

    private boolean isAuthenticated() {
        return securityContext.getUserPrincipal() != null;
    }
}
