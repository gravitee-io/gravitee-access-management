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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"role"})
public class RolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered roles for a security domain",
            notes = "User must have the DOMAIN_ROLE[READ] permission on the specified domain " +
                    "or DOMAIN_ROLE[READ] permission on the specified environment " +
                    "or DOMAIN_ROLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered roles for a security domain", response = Role.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkPermissions(or(of(ReferenceType.DOMAIN, domain, Permission.DOMAIN_ROLE, Acl.READ),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.DOMAIN_ROLE, Acl.READ),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.DOMAIN_ROLE, Acl.READ)))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> roleService.findByDomain(domain)
                                .map(roles -> {
                                    List<Role> sortedRoles = roles.stream()
                                            .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                                            .collect(Collectors.toList());
                                    return Response.ok(sortedRoles).build();
                                })))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a role",
            notes = "User must have the DOMAIN_ROLE[CREATE] permission on the specified domain " +
                    "or DOMAIN_ROLE[CREATE] permission on the specified environment " +
                    "or DOMAIN_ROLE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Role successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "role", required = true)
            @Valid @NotNull final NewRole newRole,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermissions(or(of(ReferenceType.DOMAIN, domain, Permission.DOMAIN_ROLE, Acl.CREATE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.DOMAIN_ROLE, Acl.CREATE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.DOMAIN_ROLE, Acl.CREATE)))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> roleService.create(domain, newRole, authenticatedUser)
                                .map(role -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/roles/" + role.getId()))
                                        .entity(role)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{role}")
    public RoleResource getRoleResource() {
        return resourceContext.getResource(RoleResource.class);
    }
}
