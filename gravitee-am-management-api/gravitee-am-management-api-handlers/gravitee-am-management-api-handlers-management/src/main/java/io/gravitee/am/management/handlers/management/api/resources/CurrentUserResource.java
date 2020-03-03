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

import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.manager.role.RoleManager;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"user"})
public class CurrentUserResource extends AbstractResource {

    @Autowired
    private RoleManager roleManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the current user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Current user successfully fetched", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(@Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        // prepare profile information with role permissions
        Map<String, Object> profile = new HashMap<>(authenticatedUser.getAdditionalInformation());
        profile.put("is_admin", roleManager.isAdminRoleGranted(authenticatedUser.getRoles()));
        profile.put("permissions", roleManager
                .findByIdIn(authenticatedUser.getRoles())
                .stream()
                .filter(role -> role.getScope() != null && role.getPermissions() != null)
                .map(role -> role.getPermissions().stream().map(perm -> RoleScope.valueOf(role.getScope()).name().toLowerCase() + "_" + perm).collect(Collectors.toList()))
                .flatMap(List::stream)
                .collect(Collectors.toSet()));
        profile.remove(CustomClaims.ROLES);

        response.resume(profile);
    }
}
