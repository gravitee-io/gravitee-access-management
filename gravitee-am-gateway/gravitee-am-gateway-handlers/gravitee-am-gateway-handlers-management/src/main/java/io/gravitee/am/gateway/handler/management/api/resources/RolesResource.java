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
package io.gravitee.am.gateway.handler.management.api.resources;

import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.model.NewRole;
import io.gravitee.am.model.Role;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "roles"})
public class RolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered rogles for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered roles for a security domain", response = Role.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<Role> listRoles(@PathParam("domain") String domain) {
        domainService.findById(domain);

        return roleService.findByDomain(domain)
                .stream()
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .collect(Collectors.toList());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a role")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Role successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response createRole(
            @PathParam("domain") String domain,
            @ApiParam(name = "certificate", required = true)
            @Valid @NotNull final NewRole newRole) {
        domainService.findById(domain);

        Role role = roleService.create(domain, newRole);
        if (role != null) {
            return Response
                    .created(URI.create("/domains/" + domain + "/roles/" + role.getId()))
                    .entity(role)
                    .build();
        }

        return Response.serverError().build();
    }

    @Path("{role}")
    public RoleResource getRoleResource() {
        return resourceContext.getResource(RoleResource.class);
    }
}
