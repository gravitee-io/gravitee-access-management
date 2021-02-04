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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.EmailValue;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.NewsletterService;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"user"})
@Path("/user")
public class CurrentUserResource extends AbstractResource {

    @Autowired
    private UserService userService;

    @Autowired
    private NewsletterService newsletterService;

    @Value("${newsletter.enabled:true}")
    private boolean newsletterEnabled = true;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the current user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Current user successfully fetched", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(@Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        // Get the organization the current user is logged on.
        String organizationId = (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.organization, Organization.DEFAULT);

        final Single<List<String>> organizationPermissions = permissionService.findAllPermissions(authenticatedUser, ReferenceType.ORGANIZATION, organizationId)
                .map(Permission::flatten);

        final Single<List<String>> platformPermissions = permissionService.findAllPermissions(authenticatedUser, ReferenceType.PLATFORM, Platform.DEFAULT)
                .map(Permission::flatten);

        Single.zip(platformPermissions, organizationPermissions,
                (p, o) -> {
                    Set<String> allPermissions = new HashSet<>();
                    allPermissions.addAll(p);
                    allPermissions.addAll(o);
                    return allPermissions;
                })
                .map(permissions -> {
                    // prepare profile information with role permissions
                    Map<String, Object> profile = new HashMap<>(authenticatedUser.getAdditionalInformation());
                    profile.put("permissions", permissions);
                    profile.put("newsletter_enabled", newsletterEnabled);
                    profile.remove(CustomClaims.ROLES);

                    return profile;
                }).subscribe(response::resume, response::resume);
    }

    @POST
    @Path("/subscribeNewsletter")
    @ApiOperation(value = "Subscribe to the newsletter the authenticated user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Updated user", response = User.class),
            @ApiResponse(code = 400, message = "Invalid user profile"),
            @ApiResponse(code = 404, message = "User not found"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void subscribeNewsletter(@ApiParam(name = "email", required = true) @Valid @NotNull final EmailValue emailValue,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        // Get the organization the current user is logged on.
        String organizationId = (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.organization, Organization.DEFAULT);

        userService.findById(ReferenceType.ORGANIZATION, organizationId, authenticatedUser.getId())
                .flatMap(user -> {
                    user.setEmail(emailValue.getEmail());
                    user.setNewsletter(true);
                    return userService.update(user);
                })
                .doOnSuccess(endUser -> {
                    Map<String, Object> object = new HashMap<>();
                    object.put("email", endUser.getEmail());
                    newsletterService.subscribe(object);
                })
                .subscribe(response::resume, response::resume);
    }
}
