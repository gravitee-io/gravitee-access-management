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
package io.gravitee.am.management.handlers.management.api.resources.platform.roles;

import io.gravitee.am.management.handlers.management.api.model.RoleEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.RoleService;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SystemRoleResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @GET
    @Path("{role}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a system role",
            notes = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "System role successfully fetched", response = Role.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("role") String role,
            @Suspended final AsyncResponse response) {

        // No permission needed to read system role.
        roleService.findById(ReferenceType.PLATFORM, Platform.DEFAULT, role)
                .map(this::convert)
                .subscribe(response::resume, response::resume);
    }

    private RoleEntity convert(Role role) {

        RoleEntity roleEntity = new RoleEntity(role);

        roleEntity.setAvailablePermissions(Permission.allPermissions(role.getAssignableType()).stream().map(permission -> permission.name().toLowerCase()).collect(Collectors.toList()));
        roleEntity.setPermissions(Permission.flatten(role.getPermissionAcls()));

        return roleEntity;
    }
}