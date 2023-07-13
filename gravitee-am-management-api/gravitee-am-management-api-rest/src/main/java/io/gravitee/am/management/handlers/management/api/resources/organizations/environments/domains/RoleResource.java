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
import io.gravitee.am.management.handlers.management.api.model.RoleEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "findRole",
            value = "Get a role",
            notes = "User must have the DOMAIN_ROLE[READ] permission on the specified domain " +
                    "or DOMAIN_ROLE[READ] permission on the specified environment " +
                    "or DOMAIN_ROLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Role successfully fetched", response = RoleEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("role") String role,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_ROLE, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> roleService.findById(role))
                        .switchIfEmpty(Maybe.error(new RoleNotFoundException(role)))
                        .map(role1 -> {
                            if (role1.getReferenceType() == ReferenceType.DOMAIN
                                    && !role1.getReferenceId().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Role does not belong to domain");
                            }
                            return Response.ok(convert(role1)).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateRole",
            value = "Update a role",
            notes = "User must have the DOMAIN_ROLE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_ROLE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_ROLE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Role successfully updated", response = RoleEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("role") String role,
            @ApiParam(name = "role", required = true) @Valid @NotNull UpdateRole updateRole,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_ROLE, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> roleService.update(domain, role, convert(updateRole), authenticatedUser))
                        .map(this::convert))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(
            nickname = "deleteRole",
            value = "Delete a role",
            notes = "User must have the DOMAIN_ROLE[DELETE] permission on the specified domain " +
                    "or DOMAIN_ROLE[DELETE] permission on the specified environment " +
                    "or DOMAIN_ROLE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Role successfully deleted"),
            @ApiResponse(code = 400, message = "Role is bind to existing users"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("role") String role,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_ROLE, Acl.DELETE)
                .andThen(roleService.delete(ReferenceType.DOMAIN, domain, role, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    /**
     * Starting from v3, AM role permissions and domain role permissions start to work differently.
     * AM permission are now structured using Permission and Acls while domain role permission are stil simple strings (oauth scopes).
     * Internaly, role structure has now 2 distinct attributes:
     * <ul>
     *     <li>permissions: holds the AM role permissions</li>
     *     <li>oauthScopes: holds the domain role permissions</li>
     * </ul>
     * This will be removed when we deal with this issue: https://github.com/gravitee-io/issues/issues/3323
     */
    private UpdateRole convert(UpdateRole updateDomainRole) {

        UpdateRole updateRole = new UpdateRole();
        updateRole.setDescription(updateDomainRole.getDescription());
        updateRole.setName(updateDomainRole.getName());
        updateRole.setOauthScopes(updateDomainRole.getPermissions());

        return updateRole;
    }

    /**
     * Special converter that just fill permissions from Role.oauthScope attribute for compatibility with v2.
     */
    private RoleEntity convert(Role role) {

        RoleEntity roleEntity = new RoleEntity(role);
        roleEntity.setPermissions(role.getOauthScopes());

        return roleEntity;
    }
}