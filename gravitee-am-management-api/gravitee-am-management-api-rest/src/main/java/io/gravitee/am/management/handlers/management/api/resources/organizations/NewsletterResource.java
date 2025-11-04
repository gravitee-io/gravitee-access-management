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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.EmailValue;
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.NewsletterService;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.OrganizationUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the REST resources to manage Newsletter.
 *
 * @author Titouan COMPIEGGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Newsletter")})
public class NewsletterResource extends AbstractResource {

    public static final Logger LOGGER = LoggerFactory.getLogger(NewsletterResource.class);

    @Autowired
    private NewsletterService newsletterService;

    @Autowired
    private OrganizationUserService userService;

    @POST
    @Path("/_subscribe")
    @Operation(
            operationId = "subscribeNewsletter",
            summary = "Subscribe to the newsletter the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserEntity.class))),
            @ApiResponse(responseCode = "400", description = "Invalid user profile"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void subscribeNewsletter(@Parameter(name = "email", required = true) @Valid @NotNull final EmailValue emailValue,
                                    @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        // Get the organization the current user is logged on.
        String organizationId = (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.ORGANIZATION, Organization.DEFAULT);

        userService.findById(ReferenceType.ORGANIZATION, organizationId, authenticatedUser.getId())
                .flatMap(user -> {
                    user.setEmail(emailValue.getEmail());
                    user.setNewsletter(true);
                    return userService.update(user).map(UserEntity::new);
                })
                .doOnSuccess(endUser -> {
                    Map<String, Object> object = new HashMap<>();
                    object.put("email", endUser.getEmail());
                    newsletterService.subscribe(object);
                })
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("/taglines")
    @Operation(
            operationId = "getTaglines",
            summary = "Get taglines to display in the newsletter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retrieved taglines",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getTaglines(@Suspended final AsyncResponse response) {

        newsletterService.getTaglines()
            .subscribe(
                    response::resume,
                    error -> {
                        LOGGER.error("An error has occurred when reading the newsletter taglines response", error);
                        response.resume(Collections.emptyList());
                    });
    }
}
