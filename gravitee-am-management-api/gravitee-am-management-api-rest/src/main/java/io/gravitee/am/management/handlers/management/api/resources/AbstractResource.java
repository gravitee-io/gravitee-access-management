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
import io.gravitee.am.management.service.PermissionService;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.management.service.permissions.Permissions;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractResource {

    @Context
    protected SecurityContext securityContext;

    @Autowired
    protected PermissionService permissionService;

    protected User getAuthenticatedUser() {
        if (isAuthenticated()) {
            return (User) ((UsernamePasswordAuthenticationToken) securityContext.getUserPrincipal()).getPrincipal();
        }
        return null;
    }

    protected boolean isAuthenticated() {
        return securityContext.getUserPrincipal() != null;
    }

    protected Completable checkPermission(ReferenceType referenceType, String referenceId, Permission permission, Acl... acls) {

        return checkPermissions(Permissions.of(referenceType, referenceId, permission, acls));
    }

    protected Completable checkPermissions(PermissionAcls permissionAcls) {

        return hasPermission(getAuthenticatedUser(), permissionAcls)
                .flatMapCompletable(this::checkPermission);
    }

    protected Single<Boolean> hasPermission(User user, ReferenceType referenceType, String referenceId, Permission permission, Acl... acls) {

        return hasPermission(user, Permissions.of(referenceType, referenceId, permission, acls));
    }

    protected Single<Boolean> hasPermission(User user, PermissionAcls permissionAcls) {

        return permissionService.hasPermission(user, permissionAcls);
    }

    private Completable checkPermission(Boolean hasPermission) {

        if (!hasPermission) {
            return Completable.error(new ForbiddenException("Permission denied"));
        }

        return Completable.complete();
    }
}