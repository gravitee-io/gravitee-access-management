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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.PermissionService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.reactivex.rxjava3.core.Completable;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * Base class for Automation API resources.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
public abstract class AbstractAutomationResource {

    @Context
    protected SecurityContext securityContext;

    @Autowired
    private PermissionService permissionService;

    protected User getAuthenticatedUser() {
        if (securityContext.getUserPrincipal() == null) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!(securityContext.getUserPrincipal() instanceof UsernamePasswordAuthenticationToken token)) {
            throw new NotAuthorizedException("Bearer");
        }
        return (User) token.getPrincipal();
    }

    protected Completable checkPermission(User user, ReferenceType referenceType, String referenceId, Permission permission, Acl... acls) {
        return permissionService.hasPermission(user, of(referenceType, referenceId, permission, acls))
                .flatMapCompletable(this::assertPermission);
    }

    protected Completable checkAnyPermission(User user, String organizationId, String environmentId, Permission permission, Acl... acls) {
        return permissionService.hasPermission(user,
                        or(of(ReferenceType.ENVIRONMENT, environmentId, permission, acls),
                                of(ReferenceType.ORGANIZATION, organizationId, permission, acls)))
                .flatMapCompletable(this::assertPermission);
    }

    protected Completable checkAnyPermission(User user, String organizationId, String environmentId, String domainId, Permission permission, Acl... acls) {
        return permissionService.hasPermission(user,
                        or(of(ReferenceType.DOMAIN, domainId, permission, acls),
                                of(ReferenceType.ENVIRONMENT, environmentId, permission, acls),
                                of(ReferenceType.ORGANIZATION, organizationId, permission, acls)))
                .flatMapCompletable(this::assertPermission);
    }

    private Completable assertPermission(Boolean hasPermission) {
        if (!hasPermission) {
            return Completable.error(new ForbiddenException("Permission denied"));
        }
        return Completable.complete();
    }
}
